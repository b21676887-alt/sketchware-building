package pro.sketchware.ia;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.ai.config.DeviceLanguage;
import pro.sketchware.network.AiProviderService;
import pro.sketchware.network.AiRequestHandle;

/**
* Encapsulates settings and execution for a single "generate logic" AI request.
*
* Usage:
*   LogicGenTask task = new LogicGenTask(context, userIntent, eventName, activityName, viewContext, existingCode);
*   AiRequestHandle handle = task.start(streamListener);
*
* The returned AiRequestHandle can be cancelled (handle.cancel()). This class runs
* the (synchronous) sendTextMessage API on a background thread; cancellation prevents
* delivering the result to the listener but cannot always abort the underlying HTTP call
* unless the provider layer attaches the okhttp Call to the AiRequestHandle.
*/
public final class LogicGenTask {
	
	private final Context context;
	private final String userIntent;
	private final String eventName;
	private final String activityName;
	private final String viewContext;
	private final String existingCode;
	
	private static final ExecutorService BACKGROUND = Executors.newCachedThreadPool();
	
	public LogicGenTask(Context context,
	String userIntent,
	String eventName,
	String activityName,
	String viewContext,
	String existingCode) {
		this.context = context == null ? null : context.getApplicationContext();
		this.userIntent = userIntent == null ? "" : userIntent.trim();
		this.eventName = eventName == null ? "" : eventName.trim();
		this.activityName = activityName == null ? "" : activityName.trim();
		this.viewContext = viewContext == null ? "" : viewContext.trim();
		this.existingCode = existingCode == null ? "" : existingCode.trim();
	}
	
	/**
* Start the generation. Returns an AiRequestHandle which the caller can cancel().
* Listener callbacks are invoked on the main thread.
*/	
	public AiRequestHandle start(AiProviderService.StreamListener listener) {
		final AiRequestHandle handle = new AiRequestHandle();
		final Handler main = new Handler(Looper.getMainLooper());
		
		BACKGROUND.execute(() -> {
			// bail out early if already cancelled
			if (handle.isCancelled()) {
				main.post(() -> listener.onError("cancelled", null));
				return;
			}
			
			// Select provider/model from app settings
			LayoutGeneratorModelSelector.SelectedModel selectedModel =
			LayoutGeneratorModelSelector.getCurrentChatModel(context);
			
			String providerId = selectedModel == null ? "groq" : selectedModel.providerId;
			String modelName = selectedModel == null ? "llama-3.1-8b-instant" : selectedModel.modelName;
			
			// device language instruction
			String devicelang = DeviceLanguage.responseInstruction();
			
			// Build system prompt according to provided spec
			StringBuilder systemPrompt = new StringBuilder();
			systemPrompt.append("You are generating Java logic for the \"").append(eventName)
			.append("\" event of activity \"").append(activityName)
			.append("\" inside a Sketchware Pro Android project (a visual block-based app builder that also supports raw Java). ");
			systemPrompt.append("Reply with ONLY plain Java statements that belong inside that event's body - ")
			.append("no method signature, no class wrapper, no imports, no markdown code fences, no explanation, no comments. ");
			systemPrompt.append("Reference views using the pattern binding.viewId (e.g. binding.myButton.setText(\"Hi\")), ")
			.append("which is how this project's generated activities access views.");
			
			if (!TextUtils.isEmpty(viewContext)) {
				systemPrompt.append("\n\nThe current layout has exactly these views (id: type). Use ONLY these ids via binding.<id> - never invent an id that isn't listed here:\n")
				.append(viewContext);
			} else {
				systemPrompt.append("\n\nNo views were found in the current layout, so avoid referencing any binding.<id> unless the user's request clearly implies a view that should exist.");
			}
			
			if (!TextUtils.isEmpty(existingCode)) {
				systemPrompt.append("\n\nThis event ALREADY contains the following logic:\n")
				.append(existingCode)
				.append("\n\nThe user's request below is asking you to modify or upgrade this existing logic, not replace it blindly. ")
				.append("Keep everything that still makes sense, change only what the request asks for, and return the COMPLETE updated body (not just the new/changed lines, not a diff).");
			} else {
				systemPrompt.append("\n\nThis event currently has no logic yet - write it from scratch based on the request below.");
			}
			
			systemPrompt.append(" Keep the code idiomatic Android/Java, use standard APIs, and prefer simple direct statements ")
			.append("over unnecessary helper methods so more of it can be represented as visual blocks.")
			.append("\n\n").append(devicelang);
			
			// Build user prompt
			String userPrompt = "User intent:\n" + userIntent + "\n\n"
			+ "Constraints:\n"
			+ "- Prefer Android-compatible Java (API level compatible with Sketchware projects).\n"
			+ "- Avoid external libraries unless necessary; if used, include required imports inside the code.\n"
			+ "- Return only Java code (no markdown, no numbered list, no commentary).\n";
			
			List<String> images = new ArrayList<>(); // no images for logic generation
			
			try {
				// Call synchronous text API in background thread
				String rawResponse = AiProviderService.getInstance()
				.sendTextMessage(providerId, modelName, systemPrompt.toString(), userPrompt, images);
				
				if (handle.isCancelled()) {
					main.post(() -> listener.onError("cancelled", null));
					return;
				}
				
				final String code = stripFences(rawResponse);
				main.post(() -> {
					try {
						listener.onFinalMessage(code, "");
					} catch (Exception e) {
						listener.onError("listener.exception", e);
					}
				});
			} catch (IOException ioe) {
				if (handle.isCancelled()) {
					main.post(() -> listener.onError("cancelled", ioe));
				} else {
					main.post(() -> listener.onError("io.error", ioe));
				}
			} catch (Exception e) {
				if (handle.isCancelled()) {
					main.post(() -> listener.onError("cancelled", e));
				} else {
					main.post(() -> listener.onError("gen.error", e));
				}
			}
		});
		
		return handle;
	}
	
	/** يزيل fences الثلاثية ``` أو ```java إن وُجدت، ويقص المساحات الزائدة */
	private static String stripFences(String value) {
		if (value == null) return "";
		String s = value.trim();
		
		// Standard triple-backtick fences with optional language identifier
		if (s.startsWith("```")) {
			int firstNewline = s.indexOf('\n');
			int lastFence = s.lastIndexOf("```");
			if (firstNewline >= 0 && lastFence > firstNewline) {
				s = s.substring(firstNewline + 1, lastFence).trim();
				return s;
			}
			
			// If fences present but not in the normal form, strip leading/trailing backticks permissively
			s = s.replaceAll("^```+", "").replaceAll("```+$", "").trim();
			if (!s.isEmpty()) return s;
		}
		
		// Also handle single-line inline fences (rare)
		if (s.startsWith("`") && s.endsWith("`") && s.length() > 2) {
			return s.substring(1, s.length() - 1).trim();
		}
		
		return s;
	}
}
