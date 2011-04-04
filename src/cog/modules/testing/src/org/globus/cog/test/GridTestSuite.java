
// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

package org.globus.cog.test;

/**
 * Performs test against GRIS, GRAM and FTP servers.
 */
public class GridTestSuite extends AbstractTestSuite implements TestSuiteInterface {

    /**
     * Initialized all the html files to which the output needs to be directed .
     *
     */
    public GridTestSuite(String dir, String prefix, String machinelist, int timeout) {
        super(dir, prefix, machinelist, timeout);
        setName("general");
	setServiceName(null);
        addTest(new DisplayTime());
        addTest(new DisplayHost());
        addTest(new DisplayOS());
        addTest(new TestGRIS());
        addTest(new TestFTP());
        addTest(new TestGRAM());
    }
}

