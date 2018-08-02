/*******************************************************************************
 * Copyright 2018 General Electric Company
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *******************************************************************************/

package org.eclipse.keti.acs.monitoring;

import org.eclipse.keti.acs.privilege.management.dao.GraphStartupManager;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;
import org.springframework.boot.actuate.health.Status;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.datasource.lookup.DataSourceLookupFailureException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AcsDbHealthIndicatorTest {

    private static final String IS_STARTUP_COMPLETE_FIELD_NAME = "isStartupComplete";

    @Test(dataProvider = "statuses")
    public void testHealth(final AcsMonitoringRepository acsMonitoringRepository, final Status status,
            final AcsMonitoringUtilities.HealthCode healthCode, final GraphStartupManager graphStartupManager)
            throws Exception {
        AcsDbHealthIndicator acsDbHealthIndicator = new AcsDbHealthIndicator(acsMonitoringRepository);
        acsDbHealthIndicator.setStartupManager(graphStartupManager);
        Assert.assertEquals(status, acsDbHealthIndicator.health().getStatus());
        Assert.assertEquals(AcsDbHealthIndicator.DESCRIPTION,
                acsDbHealthIndicator.health().getDetails().get(AcsMonitoringUtilities.DESCRIPTION_KEY));
        if (healthCode == AcsMonitoringUtilities.HealthCode.AVAILABLE) {
            Assert.assertFalse(acsDbHealthIndicator.health().getDetails().containsKey(AcsMonitoringUtilities.CODE_KEY));
        } else {
            Assert.assertEquals(healthCode,
                    acsDbHealthIndicator.health().getDetails().get(AcsMonitoringUtilities.CODE_KEY));
        }
    }

    @DataProvider
    public Object[][] statuses() {
        GraphStartupManager happyStartupManager = new GraphStartupManager();
        GraphStartupManager sadStartupManager = new GraphStartupManager();
        Whitebox.setInternalState(happyStartupManager, IS_STARTUP_COMPLETE_FIELD_NAME, true);
        Whitebox.setInternalState(sadStartupManager, IS_STARTUP_COMPLETE_FIELD_NAME, false);

        return new Object[][] { new Object[] { mockDbWithUp(), Status.UP, AcsMonitoringUtilities.HealthCode.AVAILABLE,
                happyStartupManager },

                { mockDbWithException(new TransientDataAccessResourceException("")), Status.DOWN,
                        AcsMonitoringUtilities.HealthCode.UNAVAILABLE, happyStartupManager },

                { mockDbWithException(new QueryTimeoutException("")), Status.DOWN,
                        AcsMonitoringUtilities.HealthCode.UNAVAILABLE, happyStartupManager },

                { mockDbWithException(new DataSourceLookupFailureException("")), Status.DOWN,
                        AcsMonitoringUtilities.HealthCode.UNREACHABLE, happyStartupManager },

                { mockDbWithException(new PermissionDeniedDataAccessException("", null)), Status.DOWN,
                        AcsMonitoringUtilities.HealthCode.MISCONFIGURATION, happyStartupManager },

                { mockDbWithException(new ConcurrencyFailureException("")), Status.DOWN,
                        AcsMonitoringUtilities.HealthCode.ERROR, happyStartupManager },

                { mockDbWithUp(), Status.DOWN, AcsMonitoringUtilities.HealthCode.MIGRATION_INCOMPLETE,
                            sadStartupManager }, };
    }

    private AcsMonitoringRepository mockDbWithUp() {
        AcsMonitoringRepository acsMonitoringRepository = Mockito.mock(AcsMonitoringRepository.class);
        Mockito.doNothing().when(acsMonitoringRepository).queryPolicySetTable();
        return acsMonitoringRepository;
    }

    private AcsMonitoringRepository mockDbWithException(final Exception e) {
        AcsMonitoringRepository acsMonitoringRepository = Mockito.mock(AcsMonitoringRepository.class);
        Mockito.doThrow(e).when(acsMonitoringRepository).queryPolicySetTable();
        return acsMonitoringRepository;
    }
}
