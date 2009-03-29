/*
 * Created on Jan 31, 2007
 */
package org.griphyn.vdl.karajan.monitor.monitors.ansi.tui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Container extends Component {
	protected List components;
	protected Component focused, oldFocused;
    
    public Container() {
        components = new ArrayList();
    }
    
    public void add(Component comp) {
    	components.add(comp);
        comp.setParent(this);
        invalidate();
    }
    
    public void remove(Component comp) {
    	components.remove(comp);
    	if (focused == comp) {
    		focused = null;
    	}
    	invalidate();
    }
    
    public List getComponents() {
    	return components;
    }
    
    public void removeAll() {
    	components.clear();
    	invalidate();
    }
    
    protected void drawTree(ANSIContext context) throws IOException {
    	super.drawTree(context);
        Iterator i = components.iterator();
        while (i.hasNext()) {
        	Component c = (Component) i.next();
        	if (c.isVisible()) {
        		drawChild(c, context);
        	}
        }
    }
    
    protected void drawChild(Component c, ANSIContext context) throws IOException {
    	c.drawTree(context);
    }

	protected void validate() {
		if (isValid()) {
			return;
		}
		Iterator i = components.iterator();
		boolean focus = false;
        while (i.hasNext()) {
        	Component c = (Component) i.next();
        	if (c.hasFocus() && !hasFocus()) {
        		focus();
        	}
            c.validate();
        }
        super.validate();
	}

	public boolean childFocused(Component component) {
	    oldFocused = focused;
		boolean f = true;
		if (focused != null) {
			f = focused.unfocus();
			if (f) {
				focused.focusLost();
			}
		}
		if (f) {
			focused = component;
			focused.focusGained();
		}
		return f;
	}
	
	public void childUnfocused(Component component) {
        if (oldFocused != null && oldFocused != component) {
            oldFocused.focus();
        }
    }
	
	public boolean keyboardEvent(Key key) {
		if (key.modALT() || key.isFunctionKey()) {
			Iterator i = components.iterator();
			while (i.hasNext()) {
				if (((Component) i.next()).keyboardEvent(key)) {
					return true;
				}
			}
			return false;
		}
		else if (focused != null) {
			return focused.keyboardEvent(key);
		}
		else {
			return false;
		}
	}
	
	public Component getFocusedComponent() {
		return focused;
	}
}
