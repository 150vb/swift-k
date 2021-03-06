/*
 * Copyright 2012 University of Chicago
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.griphyn.vdl.karajan.monitor.monitors.ansi;

import org.griphyn.vdl.karajan.monitor.SystemState;
import org.griphyn.vdl.karajan.monitor.monitors.ansi.tui.ANSI;
import org.griphyn.vdl.karajan.monitor.monitors.ansi.tui.Table;

public class BensPane extends Table {
    private SystemState state;

    public BensPane(SystemState state) {
        this.state = state;
        setModel(new BensModel(state));
        setCellRenderer(new BensCellRenderer(state));
        setColumnWidth(0, 20);
        setBgColor(ANSI.CYAN);
        setFgColor(ANSI.BLACK);
    }

    public int getHighlightBgColor() {
        return ANSI.CYAN;
    }

    public int getHighlightFgColor() {
        return ANSI.BLUE;
    }
}
