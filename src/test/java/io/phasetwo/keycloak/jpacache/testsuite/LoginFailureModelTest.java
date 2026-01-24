/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
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
 */

package io.phasetwo.keycloak.jpacache.testsuite;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;

import org.junit.Assert;
import org.junit.Test;
import org.keycloak.models.*;

public class LoginFailureModelTest extends KeycloakModelTest {

  private String realmId;

  @Override
  public void createEnvironment(KeycloakSession s) {
    RealmModel realm = createRealm(s, "realm");
    s.getContext().setRealm(realm);
    realm.setDefaultRole(
        s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
    realmId = realm.getId();
  }

  @Override
  public void cleanEnvironment(KeycloakSession s) {
    RealmModel realm = s.realms().getRealm(realmId);
    s.getContext().setRealm(realm);
    s.realms().removeRealm(realmId);
  }

  @Test
  public void testLoginFailures() {
    String userId = withRealm(realmId, (s, realm) -> s.users().addUser(realm, "user")).getId();
    UserLoginFailureModel loginFailureModel =
        withRealm(
            realmId,
            (s, realm) -> {
              UserLoginFailureProvider loginFailureProvider = s.loginFailures();
              return loginFailureProvider.addUserLoginFailure(realm, userId);
            });

    withRealm(
        realmId,
        (s, realm) -> {
          UserLoginFailureProvider loginFailureProvider = s.loginFailures();
          UserLoginFailureModel currentModel =
              loginFailureProvider.getUserLoginFailure(realm, userId);
          assertEntity(currentModel, userId, 0, null, 0, 0, 0);

          // Re-adding doesnt change anything
          UserLoginFailureModel readded = loginFailureProvider.addUserLoginFailure(realm, userId);
          assertEntity(currentModel, userId, 0, null, 0, 0, 0);

          currentModel.setLastFailure(42L);
          currentModel.setLastIPFailure("some-ip");
          currentModel.setFailedLoginNotBefore(50);
          currentModel.incrementFailures();

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          UserLoginFailureProvider loginFailureProvider = s.loginFailures();
          UserLoginFailureModel currentModel =
              loginFailureProvider.getUserLoginFailure(realm, userId);

          assertThat(currentModel.getLastFailure(), is(42L));
          assertThat(currentModel.getLastIPFailure(), is("some-ip"));
          assertThat(currentModel.getFailedLoginNotBefore(), is(50));
          assertThat(currentModel.getNumFailures(), is(1));

          currentModel.clearFailures();

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          UserLoginFailureProvider loginFailureProvider = s.loginFailures();
          UserLoginFailureModel currentModel =
              loginFailureProvider.getUserLoginFailure(realm, userId);

          assertThat(currentModel.getLastFailure(), is(0L));
          assertNull(currentModel.getLastIPFailure());
          assertThat(currentModel.getFailedLoginNotBefore(), is(0));
          assertThat(currentModel.getNumFailures(), is(0));

          loginFailureProvider.removeUserLoginFailure(realm, userId);

          return null;
        });

    String userId2 =
        withRealm(
            realmId,
            (s, realm) -> {
              UserLoginFailureProvider loginFailureProvider = s.loginFailures();

              assertNull(loginFailureProvider.getUserLoginFailure(realm, userId));

              String id2 = s.users().addUser(realm, "user2").getId();
              loginFailureProvider.addUserLoginFailure(realm, userId);
              loginFailureProvider.addUserLoginFailure(realm, id2);
              loginFailureProvider.removeAllUserLoginFailures(realm);

              return id2;
            });

    withRealm(
        realmId,
        (s, realm) -> {
          UserLoginFailureProvider loginFailureProvider = s.loginFailures();

          assertNull(loginFailureProvider.getUserLoginFailure(realm, userId));
          assertNull(loginFailureProvider.getUserLoginFailure(realm, userId2));

          return null;
        });
  }

  private static void assertEntity(
      UserLoginFailureModel entity,
      String userId,
      long lastFailure,
      String lastIpFailure,
      int failures,
      int failedLoginNotBefore,
      int temporaryLockouts) {
    Assert.assertNotNull(entity);
    Assert.assertEquals(userId, entity.getUserId());
    Assert.assertEquals(lastFailure, entity.getLastFailure());
    Assert.assertEquals(lastIpFailure, entity.getLastIPFailure());
    Assert.assertEquals(failures, entity.getNumFailures());
    Assert.assertEquals(failedLoginNotBefore, entity.getFailedLoginNotBefore());
    Assert.assertEquals(temporaryLockouts, entity.getNumTemporaryLockouts());
  }
}
