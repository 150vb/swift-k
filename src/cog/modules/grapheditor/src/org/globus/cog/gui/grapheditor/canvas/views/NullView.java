
// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

    
package org.globus.cog.gui.grapheditor.canvas.views;

import org.globus.cog.util.graph.GraphChangedEvent;

public class NullView extends AbstractView implements CanvasView{
	
    public NullView(){
        super();
        setName("Null View");
    }

    public void invalidate() {
    }

    public void graphChanged(GraphChangedEvent e) {
    }
}
