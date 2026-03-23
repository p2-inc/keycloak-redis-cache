//package io.phasetwo.keycloak.compatibility;
//
//import com.google.auto.service.AutoService;
//import io.phasetwo.keycloak.common.IsSupported;
//import lombok.extern.jbosslog.JBossLog;
//import org.infinispan.Cache;
//import org.infinispan.client.hotrod.RemoteCache;
//import org.infinispan.util.concurrent.BlockingManager;
//import org.keycloak.Config;
//import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
//import org.keycloak.connections.infinispan.InfinispanConnectionProviderFactory;
//import org.keycloak.connections.infinispan.NodeInfo;
//import org.keycloak.connections.infinispan.TopologyInfo;
//import org.keycloak.models.KeycloakSession;
//import org.keycloak.models.KeycloakSessionFactory;
//
//import java.util.concurrent.CompletionStage;
//import java.util.concurrent.ScheduledExecutorService;
//
//import static io.phasetwo.keycloak.common.ProviderHelpers.createProviderCached;
//import static org.keycloak.userprofile.DeclarativeUserProfileProviderFactory.PROVIDER_PRIORITY;
//
//@JBossLog
//@AutoService(InfinispanConnectionProviderFactory.class)
//public class NullInfinispanConnectionProviderFactory
//        implements InfinispanConnectionProviderFactory,
//                IsSupported{
//
//    @Override
//    public InfinispanConnectionProvider create(KeycloakSession session) {
//        return createProviderCached(
//                session, InfinispanConnectionProvider.class, () -> new InfinispanConnectionProvider() {
//                    @Override
//                    public <K, V> Cache<K, V> getCache(String s) {
//                        return null;
//                    }
//
//                    @Override
//                    public <K, V> Cache<K, V> getCache(String s, boolean b) {
//                        return null;
//                    }
//
//                    @Override
//                    public <K, V> RemoteCache<K, V> getRemoteCache(String s) {
//                        return null;
//                    }
//
//                    @Override
//                    public TopologyInfo getTopologyInfo() {
//                        return null;
//                    }
//
//                    @Override
//                    public NodeInfo getNodeInfo() {
//                        return null;
//                    }
//
//                    @Override
//                    public CompletionStage<Void> migrateToProtoStream() {
//                        return null;
//                    }
//
//                    @Override
//                    public ScheduledExecutorService getScheduledExecutor() {
//                        return null;
//                    }
//
//                    @Override
//                    public BlockingManager getBlockingManager() {
//                        return null;
//                    }
//
//                    @Override
//                    public void close() {}
//                });
//    }
//
//    @Override
//    public void init(Config.Scope config) {
//        log.info("Infinispan deactivated...");
//    }
//
//    @Override
//    public void postInit(KeycloakSessionFactory factory) {}
//
//    @Override
//    public void close() {}
//
//    @Override
//    public int order() {
//        return PROVIDER_PRIORITY + 1;
//    }
//
//    @Override
//    public String getId() {
//        return "default";
//    }
//}
