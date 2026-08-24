package io.github.gonalez.znpcs.npc.function;

import io.github.gonalez.znpcs.cache.CacheRegistry;
import io.github.gonalez.znpcs.modern.ModernPacketBridge;
import io.github.gonalez.znpcs.npc.FunctionContext;
import io.github.gonalez.znpcs.npc.FunctionFactory;
import io.github.gonalez.znpcs.npc.NPC;
import io.github.gonalez.znpcs.npc.NPCFunction;

public class GlowFunction extends NPCFunction {
  public GlowFunction() {
    super("glow");
  }

  protected ResultType runFunction(NPC npc, FunctionContext functionContext) {
    if (!(functionContext instanceof FunctionContext.ContextWithValue)) {
      throw new IllegalStateException("invalid context type, " + functionContext.getClass().getSimpleName()
          + ", expected ContextWithValue.");
    }
    final String glowColorName = ((FunctionContext.ContextWithValue) functionContext).getValue();

    if (ModernPacketBridge.isModern()) {
      npc.getNpcPojo().setGlowName(
          glowColorName == null || glowColorName.isEmpty() ? "WHITE" : glowColorName.toUpperCase());
      npc.setGlowColor(null);
      npc.deleteViewers();
      return ResultType.SUCCESS;
    }

    try {
      final Object glowColor = CacheRegistry.ENUM_CHAT_FORMAT_FIND.load().invoke(null,
          glowColorName == null || glowColorName.length() == 0 ? "WHITE" : glowColorName);
      if (glowColor == null) return ResultType.FAIL;
      npc.getNpcPojo().setGlowName(glowColorName);
      npc.setGlowColor(glowColor);
      CacheRegistry.SET_DATA_WATCHER_METHOD.load().invoke(
          CacheRegistry.GET_DATA_WATCHER_METHOD.load().invoke(npc.getNmsEntity()),
          CacheRegistry.DATA_WATCHER_OBJECT_CONSTRUCTOR.load().newInstance(
              0, CacheRegistry.DATA_WATCHER_REGISTER_FIELD.load()),
          !FunctionFactory.isTrue(npc, this) ? (byte) 0x40 : (byte) 0x0);
      npc.getPackets().getProxyInstance().update(npc.getPackets());
      npc.deleteViewers();
      return ResultType.SUCCESS;
    } catch (ReflectiveOperationException operationException) {
      return ResultType.FAIL;
    }
  }

  protected boolean allow(NPC npc) {
    return ModernPacketBridge.isModern() || npc.getPackets().getProxyInstance().allowGlowColor();
  }

  public NPCFunction.ResultType resolve(NPC npc) {
    if (ModernPacketBridge.isModern()) {
      return ResultType.SUCCESS;
    }
    return runFunction(npc, new FunctionContext.ContextWithValue(npc, npc.getNpcPojo().getGlowName()));
  }
}
