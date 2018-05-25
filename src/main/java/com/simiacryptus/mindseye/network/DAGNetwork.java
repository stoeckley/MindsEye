/*
 * Copyright (c) 2018 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.mindseye.network;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.simiacryptus.mindseye.lang.DataSerializer;
import com.simiacryptus.mindseye.lang.Layer;
import com.simiacryptus.mindseye.lang.LayerBase;
import com.simiacryptus.mindseye.lang.ReferenceCounting;
import com.simiacryptus.mindseye.lang.Result;
import com.simiacryptus.mindseye.lang.SerialPrecision;
import com.simiacryptus.mindseye.lang.Singleton;
import com.simiacryptus.mindseye.layers.java.WrapperLayer;
import com.simiacryptus.util.MonitoredItem;
import com.simiacryptus.util.MonitoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Directed Acyclical Graph Network The base class for all conventional network wiring.
 */
@SuppressWarnings("serial")
public abstract class DAGNetwork extends LayerBase {
  
  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(DAGNetwork.class);
  /**
   * The Input handles.
   */
  public final List<UUID> inputHandles = new ArrayList<>();
  /**
   * The Input nodes.
   */
  public final LinkedHashMap<UUID, InputNode> inputNodes = new LinkedHashMap<>();
  /**
   * The Labels.
   */
  protected final LinkedHashMap<CharSequence, UUID> labels = new LinkedHashMap<>();
  /**
   * The Nodes by id.
   */
  protected final LinkedHashMap<UUID, DAGNode> nodesById = new LinkedHashMap<>();
  
  /**
   * Instantiates a new Dag network.
   *
   * @param inputs the inputs
   */
  public DAGNetwork(final int inputs) {
    assert 0 < inputs;
    for (int i = 0; i < inputs; i++) {
      addInput();
    }
  }
  
  /**
   * Instantiates a new Dag network.
   *
   * @param json the json
   * @param rs   the rs
   */
  protected DAGNetwork(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    super(json);
    for (@Nonnull final JsonElement item : json.getAsJsonArray("inputs")) {
      @Nonnull final UUID key = UUID.fromString(item.getAsString());
      inputHandles.add(key);
      InputNode replaced = inputNodes.put(key, new InputNode(this, key));
      if (null != replaced) replaced.freeRef();
    }
    final JsonObject jsonNodes = json.getAsJsonObject("nodes");
    final JsonObject jsonLayers = json.getAsJsonObject("layers");
    final JsonObject jsonLinks = json.getAsJsonObject("links");
    final JsonObject jsonLabels = json.getAsJsonObject("labels");
    @Nonnull final Map<UUID, Layer> source_layersByNodeId = new HashMap<>();
    @Nonnull final Map<UUID, Layer> source_layersByLayerId = new HashMap<>();
    for (@Nonnull final Entry<String, JsonElement> e : jsonLayers.entrySet()) {
      @Nonnull Layer value = Layer.fromJson(e.getValue().getAsJsonObject(), rs);
      source_layersByLayerId.put(UUID.fromString(e.getKey()), value);
    }
    for (@Nonnull final Entry<String, JsonElement> e : jsonNodes.entrySet()) {
      @Nonnull final UUID nodeId = UUID.fromString(e.getKey());
      @Nonnull final UUID layerId = UUID.fromString(e.getValue().getAsString());
      final Layer layer = source_layersByLayerId.get(layerId);
      assert null != layer;
      source_layersByNodeId.put(nodeId, layer);
    }
    @Nonnull final LinkedHashMap<CharSequence, UUID> labels = new LinkedHashMap<>();
    for (@Nonnull final Entry<String, JsonElement> e : jsonLabels.entrySet()) {
      labels.put(e.getKey(), UUID.fromString(e.getValue().getAsString()));
    }
    @Nonnull final Map<UUID, List<UUID>> deserializedLinks = new HashMap<>();
    for (@Nonnull final Entry<String, JsonElement> e : jsonLinks.entrySet()) {
      @Nonnull final ArrayList<UUID> linkList = new ArrayList<>();
      for (@Nonnull final JsonElement linkItem : e.getValue().getAsJsonArray()) {
        linkList.add(UUID.fromString(linkItem.getAsString()));
      }
      deserializedLinks.put(UUID.fromString(e.getKey()), linkList);
    }
    for (final UUID key : labels.values()) {
      initLinks(deserializedLinks, source_layersByNodeId, key);
    }
    @Nonnull final UUID head = UUID.fromString(json.getAsJsonPrimitive("head").getAsString());
    initLinks(deserializedLinks, source_layersByNodeId, head);
    source_layersByLayerId.values().forEach(x -> x.freeRef());
    this.labels.putAll(labels);
    assertConsistent();
  }
  
  /**
   * Add dag node.
   *
   * @param nextHead the next head
   * @param head     the head
   * @return the dag node
   */
  @Nullable
  public InnerNode add(@Nonnull final Layer nextHead, final DAGNode... head) {
    return add(null, nextHead, head);
  }
  
  /**
   * Wrap dag node.
   *
   * @param nextHead the next head
   * @param head     the head
   * @return the dag node
   */
  @Nullable
  public InnerNode wrap(@Nonnull final Layer nextHead, final DAGNode... head) {
    InnerNode add = add(null, nextHead, head);
    nextHead.freeRef();
    return add;
  }
  
  /**
   * Add dag node.
   *
   * @param label the label
   * @param layer the layer
   * @param head  the head
   * @return the dag node
   */
  public InnerNode add(@Nullable final CharSequence label, @Nonnull final Layer layer, final DAGNode... head) {
    assertAlive();
    assertConsistent();
    assert null != getInput();
    @Nonnull final InnerNode node = new InnerNode(this, layer, head);
    LinkedHashMap<Object, Layer> layersById = getLayersById();
    synchronized (layersById) {
      if (!layersById.containsKey(layer.getId())) {
        Layer replaced = layersById.put(layer.getId(), layer);
        layer.addRef();
        if (null != replaced) replaced.freeRef();
      }
    }
    DAGNode replaced = nodesById.put(node.getId(), node);
    if (null != replaced) replaced.freeRef();
    if (null != label) {
      labels.put(label, node.getId());
    }
    assertConsistent();
    return node;
  }
  
  @Override
  protected void _free() {
    super._free();
    this.nodesById.values().forEach(ReferenceCounting::freeRef);
    this.inputNodes.values().forEach(ReferenceCounting::freeRef);
  }
  
  /**
   * Add input nn layer.
   *
   * @return the nn layer
   */
  @Nonnull
  public Layer addInput() {
    @Nonnull final UUID key = UUID.randomUUID();
    inputHandles.add(key);
    InputNode replaced = inputNodes.put(key, new InputNode(this, key));
    if (null != replaced) replaced.freeRef();
    return this;
  }
  
  /**
   * Assert consistent boolean.
   *
   * @return the boolean
   */
  protected boolean assertConsistent() {
    assert null != getInput();
    for (@Nonnull final Entry<CharSequence, UUID> e : labels.entrySet()) {
      assert nodesById.containsKey(e.getValue());
    }
    return true;
  }
  
  /**
   * Attach.
   *
   * @param obj the obj
   */
  public void attach(@Nonnull final MonitoredObject obj) {
    visitLayers(layer -> {
      if (layer instanceof MonitoredItem) {
        obj.addObj(layer.getName(), (MonitoredItem) layer);
      }
    });
  }
  
  /**
   * Build handler ctx graph evaluation context.
   *
   * @param inputs the inputs
   * @return the graph evaluation context
   */
  @Nonnull
  public GraphEvaluationContext buildExeCtx(@Nonnull final Result... inputs) {
    assert inputs.length == inputHandles.size() : inputs.length + " != " + inputHandles.size();
    @Nonnull final GraphEvaluationContext context = new GraphEvaluationContext();
    for (int i = 0; i < inputs.length; i++) {
      UUID key = inputHandles.get(i);
      Result input = inputs[i];
      if (!context.calculated.containsKey(key)) {
        input.getData().addRef();
        context.calculated.put(key, new Singleton<CountingResult>().set(new CountingResult(input)));
      }
    }
    context.expectedCounts.putAll(getNodes().stream().flatMap(t -> {
      return Arrays.stream(t.getInputs()).map(n -> n.getId());
    }).filter(x -> !inputHandles.contains(x)).collect(Collectors.groupingBy(x -> x, Collectors.counting())));
    return context;
  }
  
  @Nonnull
  @Override
  public DAGNetwork copy(SerialPrecision precision) {
    return (DAGNetwork) super.copy(precision);
  }
  
  @Nullable
  @Override
  public Result eval(final Result... input) {
    assertAlive();
    @Nonnull GraphEvaluationContext buildExeCtx = buildExeCtx(input);
    @Nullable Result result;
    try {
      result = getHead().get(buildExeCtx);
    } finally {
      buildExeCtx.freeRef();
    }
    return result;
  }
  
  /**
   * Gets by label.
   *
   * @param key the key
   * @return the by label
   */
  public DAGNode getByLabel(final CharSequence key) {
    return nodesById.get(labels.get(key));
  }
  
  /**
   * Gets by name.
   *
   * @param <T>  the type parameter
   * @param name the name
   * @return the by name
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public <T extends Layer> T getByName(@Nullable final CharSequence name) {
    if (null == name) return null;
    @Nonnull final AtomicReference<Layer> result = new AtomicReference<>();
    visitLayers(n -> {
      if (name.equals(n.getName())) {
        result.set(n);
      }
    });
    return (T) result.get();
  }
  
  /**
   * Gets child node.
   *
   * @param id the id
   * @return the child node
   */
  public DAGNode getChildNode(final UUID id) {
    if (nodesById.containsKey(id)) {
      return nodesById.get(id);
    }
    return nodesById.values().stream().map(x -> x.getLayer())
      .filter(x -> x instanceof DAGNetwork)
      .map(x -> ((DAGNetwork) x).getChildNode(id))
      .filter(x -> x != null).findAny().orElse(null);
  }
  
  @Override
  public List<Layer> getChildren() {
    return getLayersById().values().stream().flatMap(l -> l.getChildren().stream()).distinct().sorted(Comparator.comparing(l -> l.getId().toString())).collect(Collectors.toList());
  }
  
  private DAGNode[] getDependencies(@Nonnull final Map<UUID, List<UUID>> deserializedLinks, final UUID e) {
    final List<UUID> links = deserializedLinks.get(e);
    if (null == links) return new DAGNode[]{};
    return links.stream().map(id -> getNode(id)).toArray(i -> new DAGNode[i]);
  }
  
  /**
   * Gets head.
   *
   * @return the head
   */
  @Nullable
  public abstract DAGNode getHead();
  
  /**
   * Gets input.
   *
   * @return the input
   */
  @Nonnull
  public List<DAGNode> getInput() {
    @Nonnull final ArrayList<DAGNode> list = new ArrayList<>();
    for (final UUID key : inputHandles) {
      list.add(inputNodes.get(key));
    }
    return list;
  }
  
  /**
   * Gets input.
   *
   * @param index the index
   * @return the input
   */
  public DAGNode getInput(final int index) {
    final DAGNode input = inputNodes.get(inputHandles.get(index));
    assert null != input;
    return input;
  }
  
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJsonStub();
    @Nonnull final JsonArray inputs = new JsonArray();
    json.add("inputs", inputs);
    inputHandles.forEach(uuid -> inputs.add(new JsonPrimitive(uuid.toString())));
    @Nonnull final JsonObject layerMap = new JsonObject();
    @Nonnull final JsonObject nodeMap = new JsonObject();
    @Nonnull final JsonObject links = new JsonObject();
    nodesById.values().forEach(node -> {
      @Nonnull final JsonArray linkArray = new JsonArray();
      Arrays.stream(node.getInputs()).forEach((@Nonnull final DAGNode input) -> linkArray.add(new JsonPrimitive(input.getId().toString())));
      @Nullable final Layer layer = node.getLayer();
      @Nonnull final String nodeId = node.getId().toString();
      final String layerId = layer.getId().toString();
      nodeMap.addProperty(nodeId, layerId);
      layerMap.add(layerId, layer.getJson(resources, dataSerializer));
      links.add(nodeId, linkArray);
    });
    json.add("nodes", nodeMap);
    json.add("layers", layerMap);
    json.add("links", links);
    @Nonnull final JsonObject labels = new JsonObject();
    this.labels.forEach((k, v) -> {
      labels.addProperty(k.toString(), v.toString());
    });
    json.add("labels", labels);
    json.addProperty("head", getHead().getId().toString());
    return json;
  }
  
  /**
   * Gets layer.
   *
   * @return the layer
   */
  @Nonnull
  public Layer getLayer() {
    return this;
  }
  
  private DAGNode getNode(final UUID id) {
    DAGNode returnValue = nodesById.get(id);
    if (null == returnValue) {
      returnValue = inputNodes.get(id);
    }
    return returnValue;
  }
  
  /**
   * Gets nodes.
   *
   * @return the nodes
   */
  public List<DAGNode> getNodes() {
    return Stream.concat(
      nodesById.values().stream(),
      getInput().stream()
    ).collect(Collectors.toList());
  }
  
  private synchronized void initLinks(@Nonnull final Map<UUID, List<UUID>> nodeLinks, @Nonnull final Map<UUID, Layer> layersByNodeId, final UUID newNodeId) {
    LinkedHashMap<Object, Layer> layersById = getLayersById();
    if (layersById.containsKey(newNodeId)) return;
    if (inputNodes.containsKey(newNodeId)) return;
    final Layer layer = layersByNodeId.get(newNodeId);
    if (layer == null) {
      throw new IllegalArgumentException(String.format("%s is linked to but not defined", newNodeId));
    }
    final List<UUID> links = nodeLinks.get(newNodeId);
    if (null != links) {
      for (final UUID link : links) {
        initLinks(nodeLinks, layersByNodeId, link);
      }
    }
    assertConsistent();
    final DAGNode[] dependencies = getDependencies(nodeLinks, newNodeId);
    @Nonnull final InnerNode node = new InnerNode(this, layer, newNodeId, dependencies);
    if (!layersById.containsKey(layer.getId())) {
      Layer replaced = layersById.put(layer.getId(), layer);
      layer.addRef();
      if (null != replaced) replaced.freeRef();
    }
    DAGNode replaced = nodesById.put(node.getId(), node);
    if (null != replaced) replaced.freeRef();
    assertConsistent();
  }
  
  /**
   * Remove last input nn layer.
   *
   * @return the nn layer
   */
  @Nonnull
  public Layer removeLastInput() {
    final int index = inputHandles.size() - 1;
    final UUID key = inputHandles.remove(index);
    InputNode remove = inputNodes.remove(key);
    if (null != remove) remove.freeRef();
    return this;
  }
  
  /**
   * Reset.
   */
  public synchronized void reset() {
    getLayersById().values().forEach(x -> x.freeRef());
    nodesById.values().forEach(x -> x.freeRef());
    nodesById.clear();
    labels.clear();
  }
  
  @Nonnull
  @Override
  public DAGNetwork setFrozen(final boolean frozen) {
    super.setFrozen(frozen);
    visitLayers(layer -> layer.setFrozen(frozen));
    return this;
  }
  
  @Override
  public List<double[]> state() {
    return getChildren().stream().flatMap(l -> l.state().stream()).distinct().collect(Collectors.toList());
  }
  
  /**
   * Visit layers.
   *
   * @param visitor the visitor
   */
  public void visitLayers(@Nonnull final Consumer<Layer> visitor) {
    visitNodes(node -> {
      Layer layer = node.getLayer();
      Layer unwrapped = layer;
      while (unwrapped instanceof WrapperLayer) {
        unwrapped = ((WrapperLayer) unwrapped).getInner();
      }
      if (unwrapped instanceof DAGNetwork) {
        ((DAGNetwork) unwrapped).visitLayers(visitor);
      }
      visitor.accept(layer);
      while (layer instanceof WrapperLayer) {
        Layer inner = ((WrapperLayer) layer).getInner();
        visitor.accept(inner);
        layer = inner;
      }
    });
  }
  
  /**
   * Visit nodes.
   *
   * @param visitor the visitor
   */
  public void visitNodes(@Nonnull final Consumer<DAGNode> visitor) {
    nodesById.values().forEach(node -> {
      Layer layer = node.getLayer();
      while (layer instanceof WrapperLayer) {
        layer = ((WrapperLayer) layer).getInner();
      }
      if (layer instanceof DAGNetwork) {
        ((DAGNetwork) layer).visitNodes(visitor);
      }
      visitor.accept(node);
    });
  }
  
  public DAGNetwork scrambleCopy() {
    return scrambleCopy(new HashMap<>());
  }
  
  @Nonnull
  public DAGNetwork scrambleCopy(final Map<String, String> replacements) {
    assertAlive();
    @Nonnull HashMap<CharSequence, byte[]> resources = new HashMap<>();
    final JsonObject json = getJson(resources, SerialPrecision.Double);
    replacements.putAll(Stream.concat(
      Stream.of(getId()),
      Stream.concat(
        getLayersById().keySet().stream(),
        nodesById.keySet().stream()
      )
    ).map(x -> x.toString()).distinct().collect(Collectors.toMap(x -> x, x -> UUID.randomUUID().toString())));
    String[] jsonString = {json.toString()};
    replacements.forEach((k, v) -> {
      String regex = k.replaceAll("\\-", "\\\\-?");
      log.info(String.format("%s (%s) => %s", k, regex, v));
      jsonString[0] = jsonString[0].replaceAll(regex, v);
    });
    return (DAGNetwork) Layer.fromJson(new GsonBuilder().create().fromJson(jsonString[0], JsonObject.class).getAsJsonObject(), resources);
  }
  
  /**
   * The Layers by id.
   */
  public LinkedHashMap<Object, Layer> getLayersById() {
    LinkedHashMap<Object, Layer> map = new LinkedHashMap<>();
    visitLayers(layer -> {
      Object id = layer.getId();
      Layer previous = map.put(id, layer);
      if (null != previous && previous != layer)
        throw new RuntimeException(String.format("Duplicated layer found: %s (%s)", previous, id));
    });
    return map;
  }
}
