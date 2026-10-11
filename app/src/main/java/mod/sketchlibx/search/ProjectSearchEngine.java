package mod.sketchlibx.search;

import android.text.TextUtils;
import android.util.Pair;

import pro.sketchware.beans.BlockBean;
import pro.sketchware.beans.ComponentBean;
import pro.sketchware.beans.ProjectFileBean;
import pro.sketchware.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.core.project.ProjectDataManager;

public class ProjectSearchEngine {

    private final String sc_id;

    public ProjectSearchEngine(String sc_id) {
        this.sc_id = sc_id;
    }

    public List<SearchResult> search(String query, String filter) {
        List<SearchResult> results = new ArrayList<>();
        if (TextUtils.isEmpty(query)) return results;

        String q = query.toLowerCase();
        
        boolean searchViews = filter.equals("All") || filter.equals("Views");
        boolean searchLogic = filter.equals("All") || filter.equals("Logic");
        boolean searchComps = filter.equals("All") || filter.equals("Components");
        
        // استخدام الدوال الصحيحة المعرفة في ProjectFileManager
        ArrayList<ProjectFileBean> allFiles = ProjectDataManager.getFileManager(sc_id).getActivities();
        if (allFiles == null) return results;

        for (ProjectFileBean file : allFiles) {
            String xmlName = file.getXmlName();
            String javaName = file.getJavaName();

            // 1. SCAN VIEWS (XML UI)
            if (searchViews && !TextUtils.isEmpty(xmlName)) {
                // استخدام getViews المعرفة في ProjectDataStore
                ArrayList<ViewBean> views = ProjectDataManager.getProjectDataManager(sc_id).getViews(xmlName);
                if (views != null) {
                    for (ViewBean view : views) {
                        if (view.id.toLowerCase().contains(q)) {
                            results.add(new SearchResult(
                                    xmlName, "View", 
                                    view.id, 
                                    "Found in " + xmlName, 0, view.id, null));
                        }
                    }
                }
            }

            // 2. SCAN COMPONENTS & VARIABLES
            if (searchComps && !TextUtils.isEmpty(javaName)) {
                // استخدام getComponents المعرفة في ProjectDataStore
                ArrayList<ComponentBean> components = ProjectDataManager.getProjectDataManager(sc_id).getComponents(javaName);
                if (components != null) {
                    for (ComponentBean comp : components) {
                        if (comp.componentId.toLowerCase().contains(q)) {
                            results.add(new SearchResult(
                                    javaName, "Component", 
                                    comp.componentId, 
                                    "Found in " + javaName, 2, comp.componentId, null));
                        }
                    }
                }

                // استخدام getVariables المعرفة في ProjectDataStore
                ArrayList<Pair<Integer, String>> variables = ProjectDataManager.getProjectDataManager(sc_id).getVariables(javaName);
                if (variables != null) {
                    for (Pair<Integer, String> var : variables) {
                        if (var.second.toLowerCase().contains(q)) {
                            results.add(new SearchResult(
                                    javaName, "Variable", 
                                    var.second, "Found in " + javaName, 2, var.second, null));
                        }
                    }
                }

                // استخدام getListVariables المعرفة في ProjectDataStore
                ArrayList<Pair<Integer, String>> lists = ProjectDataManager.getProjectDataManager(sc_id).getListVariables(javaName);
                if (lists != null) {
                    for (Pair<Integer, String> list : lists) {
                        if (list.second.toLowerCase().contains(q)) {
                            results.add(new SearchResult(
                                    javaName, "List", 
                                    list.second, "Found in " + javaName, 2, list.second, null));
                        }
                    }
                }
            }

            // 3. SCAN LOGIC BLOCKS & EVENTS
            if (searchLogic && !TextUtils.isEmpty(javaName)) {
                // استخدام getBlockMap المعرفة في ProjectDataStore
                HashMap<String, ArrayList<BlockBean>> events = ProjectDataManager.getProjectDataManager(sc_id).getBlockMap(javaName);
                if (events != null) {
                    for (Map.Entry<String, ArrayList<BlockBean>> entry : events.entrySet()) {
                        String eventKey = entry.getKey(); 
                        String targetId = eventKey;
                        String eventName = "";
                        
                        int lastUnderscore = eventKey.lastIndexOf("_");
                        if (lastUnderscore != -1) {
                            targetId = eventKey.substring(0, lastUnderscore);
                            eventName = eventKey.substring(lastUnderscore + 1);
                        }

                        ArrayList<BlockBean> blocks = entry.getValue();
                        if (blocks != null) {
                            for (BlockBean block : blocks) {
                                boolean matched = block.opCode != null && block.opCode.toLowerCase().contains(q);
                                if (!matched && block.parameters != null) {
                                    for (String param : block.parameters) {
                                        if (param != null && param.toLowerCase().contains(q)) {
                                            matched = true;
                                            break;
                                        }
                                    }
                                }
                                
                                if (matched) {
                                    results.add(new SearchResult(
                                            javaName, "Logic Block", 
                                            "Event: " + eventKey, 
                                            "Block: " + block.opCode, 1, targetId, eventName));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        return results;
    }
}
