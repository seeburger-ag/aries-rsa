package org.apache.aries.rsa.discovery.zookeeper.repository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.aries.rsa.discovery.endpoint.EndpointDescriptionParser;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZookeeperEndpointRepository implements Closeable, Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperEndpointRepository.class);
    private final ZooKeeper zk;
    private final EndpointDescriptionParser parser;
    private EndpointEventListener listener;
    public static final String PATH_PREFIX = "/osgi/service_registry";
    
    private Map<String, EndpointDescription> nodes = new ConcurrentHashMap<String, EndpointDescription>();
    
    public ZookeeperEndpointRepository(ZooKeeper zk) {
        this(zk, null);
    }
    
    public ZookeeperEndpointRepository(ZooKeeper zk, EndpointEventListener listener) {
        this.zk = zk;
        this.listener = listener;
        this.parser = new EndpointDescriptionParser();
        try {
            createPath(PATH_PREFIX);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create base path");
        }
        // Not yet needed
        //this.registerWatcher();
    }

    private void registerWatcher() {
        try {
            List<String> children = zk.getChildren(ZookeeperEndpointRepository.PATH_PREFIX, this);
            System.out.println(children);
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    protected void notifyListener(WatchedEvent wevent) {
        EndpointDescription ep = read(wevent.getPath());
        if (ep != null) {
            int type = getEndpointEventType(wevent);
            EndpointEvent event = new EndpointEvent(type, ep);
            listener.endpointChanged(event, null);
        }
    }
    
    private int getEndpointEventType(WatchedEvent wevent) {
        EventType type = wevent.getType();
        return EndpointEvent.ADDED;
    }

    /**
     * Retrieves data from the given node and parses it into an EndpointDescription.
     *
     * @param path a node path
     * @return endpoint found in the node or null if no endpoint was found
     */
    public EndpointDescription read(String path) {
        try {
            Stat stat = zk.exists(path, false);
            if (stat == null || stat.getDataLength() <= 0) {
                return null;
            }
            byte[] data = zk.getData(path, false, null);
            LOG.debug("Got data for node: {}", path);

            EndpointDescription endpoint = parser.readEndpoint(new ByteArrayInputStream(data));
            if (endpoint != null) {
                return endpoint;
            }
            LOG.warn("No Discovery information found for node: {}", path);
        } catch (Exception e) {
            LOG.error("Problem getting EndpointDescription from node " + path, e);
        }
        return null;
    }

    public void add(EndpointDescription endpoint) throws URISyntaxException, KeeperException,
    InterruptedException, IOException {
        Collection<String> interfaces = endpoint.getInterfaces();
        String endpointKey = getKey(endpoint);
    
        LOG.info("Exporting endpoint to zookeeper: {}", endpoint);
        for (String name : interfaces) {
            String path = ZookeeperEndpointRepository.getZooKeeperPath(name);
            String fullPath = path + '/' + endpointKey;
            LOG.info("Creating ZooKeeper node for service with path {}", fullPath);
            createPath(path);
            createEphemeralNode(fullPath, getData(endpoint));
        }
    }

    public void modify(EndpointDescription endpoint) throws URISyntaxException, KeeperException, InterruptedException {
        Collection<String> interfaces = endpoint.getInterfaces();
        String endpointKey = getKey(endpoint);

        LOG.info("Changing endpoint in zookeeper: {}", endpoint);
        for (String name : interfaces) {
            String path = ZookeeperEndpointRepository.getZooKeeperPath(name);
            String fullPath = path + '/' + endpointKey;
            LOG.info("Changing ZooKeeper node for service with path {}", fullPath);
            createPath(path);
            zk.setData(fullPath, getData(endpoint), -1);
        }
    }
    
    public void remove(EndpointDescription endpoint) throws UnknownHostException, URISyntaxException {
        Collection<String> interfaces = endpoint.getInterfaces();
        String endpointKey = getKey(endpoint);
        for (String name : interfaces) {
            String path = ZookeeperEndpointRepository.getZooKeeperPath(name);
            String fullPath = path + '/' + endpointKey;
            LOG.debug("Removing ZooKeeper node: {}", fullPath);
            try {
                zk.delete(fullPath, -1);
            } catch (Exception ex) {
                LOG.debug("Error while removing endpoint: {}", ex); // e.g. session expired
            }
        }
    }
    
    public List<EndpointDescription> getAll() throws KeeperException, InterruptedException {
        return null;
    }

    @Override
    public void close() throws IOException {

    }

    private byte[] getData(EndpointDescription epd) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        parser.writeEndpoint(epd, bos);
        return bos.toByteArray();
    }
    
    private void createEphemeralNode(String fullPath, byte[] data) throws KeeperException, InterruptedException {
        try {
            zk.create(fullPath, data, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } catch (NodeExistsException nee) {
            // this sometimes happens after a ZooKeeper node dies and the ephemeral node
            // that belonged to the old session was not yet deleted. We need to make our
            // session the owner of the node so it won't get deleted automatically -
            // we do this by deleting and recreating it ourselves.
            LOG.info("node for endpoint already exists, recreating: {}", fullPath);
            try {
                zk.delete(fullPath, -1);
            } catch (NoNodeException nne) {
                // it's a race condition, but as long as it got deleted - it's ok
            }
            zk.create(fullPath, data, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        }
    }
    
    private void createPath(String path) throws KeeperException, InterruptedException {
        StringBuilder current = new StringBuilder();
        List<String> parts = ZookeeperEndpointRepository.removeEmpty(Arrays.asList(path.split("/")));
        for (String part : parts) {
            current.append('/');
            current.append(part);
            try {
                if (zk.exists(current.toString(), false) == null) {
                    zk.create(current.toString(), new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                }
            } catch (NodeExistsException nee) {
                // it's not the first node with this path to ever exist - that's normal
            }
        }
    }

    /**
     * Removes nulls and empty strings from the given string array.
     *
     * @param strings an array of strings
     * @return a new array containing the non-null and non-empty
     *         elements of the original array in the same order
     */
    public static List<String> removeEmpty(List<String> strings) {
        List<String> result = new ArrayList<String>();
        if (strings == null) {
            return result;
        }
        for (String s : strings) {
            if (s != null && !s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }

    public static String getZooKeeperPath(String name) {
        return name == null || name.isEmpty() ? PATH_PREFIX : PATH_PREFIX + '/' + name.replace('.', '/');
    }

    private static String getKey(EndpointDescription endpoint) throws URISyntaxException {
        URI uri = new URI(endpoint.getId());
        return new StringBuilder().append(uri.getHost()).append("#").append(uri.getPort())
            .append("#").append(uri.getPath().replace('/', '#')).toString();
    }

    @Override
    public void process(WatchedEvent event) {
        
    }

}
