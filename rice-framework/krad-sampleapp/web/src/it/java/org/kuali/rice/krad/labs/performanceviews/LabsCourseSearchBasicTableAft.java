/**
 * Copyright 2005-2015 The Kuali Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.opensource.org/licenses/ecl2.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kuali.rice.krad.labs.performanceviews;

import org.junit.Test;
import org.kuali.rice.testtools.selenium.WebDriverLegacyITBase;

/**
 * @author Kuali Rice Team (rice.collab@kuali.org)
 */
public class LabsCourseSearchBasicTableAft extends WebDriverLegacyITBase {

    /**
     * /kr-krad/kitchensinkperformance?viewId=Demo-Performance-BasicTableCollection
     */
    public static final String BOOKMARK_URL = "/kr-krad/kitchensinkperformance?viewId=Demo-Performance-BasicTableCollection";

    @Override
    protected String getBookmarkUrl() {
        return BOOKMARK_URL;
    }

    @Override
    protected void navigate() throws Exception {
    	waitAndClickByLinkText("Course Search Basic Table");
    }

    protected void testCourseSearchBasicTable() throws InterruptedException {
    	waitAndClickButtonByExactText("Generate Table");
    	waitForElementPresentByXpath("//table[@class='table table-condensed table-bordered uif-tableCollectionLayout']");
    }

    @Test
    public void testCourseSearchBasicTableBookmark() throws Exception {
    	testCourseSearchBasicTable();
        passed();
    }

    @Test
    public void testCourseSearchBasicTableNav() throws Exception {
    	testCourseSearchBasicTable();
        passed();
    }
}
