package com.scaleunlimited.wikiwords;

import java.util.ArrayList;
import java.util.List;

public class XMLPath {
    
    private List<String> _path;
    
    public XMLPath() {
        _path = new ArrayList<>();
    }

    public void pushNode(String qName) {
        _path.add(qName);
    }
    
    public void popNode(String qName) {
        // TODO verify that last item is == qName
        _path.remove(_path.size() - 1);
    }
    
    public boolean atNode(String nodePath) {
        String[] nodeNames = nodePath.split("/");
        boolean isAbsolutePath = nodePath.startsWith("/");

        if (nodeNames.length > _path.size()) {
            return false;
        } else if (isAbsolutePath && (nodeNames.length != _path.size())) {
            return false;
        }

        
        int targetPathIndex = nodeNames.length - 1;
        int curPathIndex = _path.size() - 1;
        for (; targetPathIndex >= 0; targetPathIndex--, curPathIndex--) {
            if (!nodeNames[targetPathIndex].equals(_path.get(curPathIndex))) {
                return false;
            }
        }

        return true;
    }
    
    /**
     * Return true if our current path contains <nodePath>. This means we're at or
     * below (deeper) in the document hierarchy than whatever is in <nodePath>
     * 
     * @param nodePath
     * @return
     */
    public boolean inNode(String nodePath) {
        String[] nodeNames = nodePath.split("/");
        boolean isAbsolutePath = nodePath.startsWith("/");

        for (int startIndex = 0; startIndex <= _path.size() - nodeNames.length; startIndex++) {
            int targetPathIndex = 0;
            int curPathIndex = startIndex;

            for (; targetPathIndex < nodeNames.length; targetPathIndex++, curPathIndex++) {
                if (!nodeNames[targetPathIndex].equals(_path.get(curPathIndex))) {
                    if (isAbsolutePath) {
                        return false;
                    } else {
                        break;
                    }
                }
            }
            
            // If we matched the entire <nodePath> we're good.
            if (targetPathIndex == nodeNames.length){
                return true;
            }
        }

        return false;
    }
}
