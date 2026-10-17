package net.neoforged.forgestuck.transformer.jar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Statically evaluates legacy Forge enum-extension factory calls so that NeoForge 1.21.1 {@code enumextensions.json}
 * entries can be generated. Forge 1.20.1 created {@code ItemDisplayContext} constants by calling
 * {@code ItemDisplayContext.create(String, ResourceLocation, ItemDisplayContext)} at any time; NeoForge only allows
 * constants declared in JSON before the enum class loads.
 *
 * <p>The evaluator is a small concrete interpreter over a jar's own bytecode. It executes the {@code <clinit>} and
 * no-argument static methods of every class referring to the enum, following same-jar static calls (with their real
 * arguments), and records the arguments of each {@code create} call when they are all constants. Anything it cannot
 * prove (unknown values reaching a branch or the call, instance state, non-static roots) is left unresolved and
 * reported; such a call site then fails precisely at runtime, never silently.
 */
public final class LegacyEnumExtensionPlanner {
    public static final String ITEM_DISPLAY_CONTEXT = "net/minecraft/world/item/ItemDisplayContext";
    static final String CREATE_DESC =
            "(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;L" + ITEM_DISPLAY_CONTEXT + ";)L"
                    + ITEM_DISPLAY_CONTEXT + ";";
    private static final String RESOURCE_LOCATION = "net/minecraft/resources/ResourceLocation";
    private static final int MAX_STEPS = 50_000;
    private static final int MAX_DEPTH = 8;

    /** A fully evaluated {@code create} call. {@code fallback} is the enum constant name or {@code null}. */
    public record Entry(String keyName, String id, String fallback) {
        public String namespace() {
            return id.substring(0, id.indexOf(':'));
        }
    }

    /** Result of planning one jar. */
    public record Plan(List<Entry> entries, List<String> unresolvedSites) {}

    private static final Object UNKNOWN = new Object() {
        @Override
        public String toString() {
            return "<unknown>";
        }
    };
    private static final Object NULL = new Object();
    private static final Object LOCALE_ROOT = new Object();

    private record EnumRef(String name) {}

    private record Rl(String namespace, String path) {
        @Override
        public String toString() {
            return namespace + ":" + path;
        }
    }

    private static final class Pending {
        Object value = UNKNOWN;
    }

    private static final class Abort extends RuntimeException {
        Abort(String message) {
            super(message, null, false, false);
        }
    }

    private final Function<String, byte[]> classSource;
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final Map<String, Object> statics = new HashMap<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Set<String> resolvedSites = new java.util.HashSet<>();
    private int steps;

    private LegacyEnumExtensionPlanner(Function<String, byte[]> classSource) {
        this.classSource = classSource;
    }

    /**
     * @param candidates internal names of the classes that mention {@code ItemDisplayContext}
     * @param classSource loads the original bytes of any class of the same jar by internal name, or {@code null}
     */
    public static Plan plan(List<String> candidates, Function<String, byte[]> classSource) {
        LegacyEnumExtensionPlanner planner = new LegacyEnumExtensionPlanner(classSource);
        List<String> sites = new ArrayList<>();
        for (String candidate : candidates) {
            ClassNode node = planner.load(candidate);
            if (node == null) continue;
            for (MethodNode method : node.methods) {
                if (callsCreate(method)) sites.add(node.name + "." + method.name + method.desc);
            }
        }
        for (String candidate : candidates) {
            ClassNode node = planner.load(candidate);
            if (node == null) continue;
            for (MethodNode method : node.methods) {
                boolean root = (method.access & Opcodes.ACC_STATIC) != 0
                        && (method.name.equals("<clinit>") || method.desc.startsWith("()"))
                        && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0;
                if (!root) continue;
                planner.steps = 0;
                try {
                    planner.run(node, method, new Object[method.maxLocals], 0);
                } catch (Abort ignored) {
                    // root could not be evaluated further; events recorded before the abort remain valid
                }
            }
        }
        List<String> unresolved = new ArrayList<>();
        for (String site : sites) {
            if (!planner.resolvedSites.contains(site)) unresolved.add(site);
        }
        return new Plan(List.copyOf(planner.entries.values()), unresolved);
    }

    static boolean callsCreate(MethodNode method) {
        if (method.instructions == null) return false;
        for (AbstractInsnNode insn : method.instructions) {
            if (isCreate(insn)) return true;
        }
        return false;
    }

    private static boolean isCreate(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals(ITEM_DISPLAY_CONTEXT) && call.name.equals("create") && call.desc.equals(CREATE_DESC);
    }

    private ClassNode load(String internalName) {
        if (classes.containsKey(internalName)) return classes.get(internalName);
        byte[] bytes = classSource.apply(internalName);
        ClassNode node = null;
        if (bytes != null) {
            node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG);
        }
        classes.put(internalName, node);
        return node;
    }

    private Object run(ClassNode owner, MethodNode method, Object[] locals, int depth) {
        if (depth > MAX_DEPTH) throw new Abort("call depth");
        InsnList list = method.instructions;
        AbstractInsnNode[] code = list.toArray();
        Map<LabelNode, Integer> labels = new HashMap<>();
        for (int i = 0; i < code.length; i++) {
            if (code[i] instanceof LabelNode label) labels.put(label, i);
        }
        Object[] vars = java.util.Arrays.copyOf(locals, Math.max(locals.length, method.maxLocals));
        ArrayList<Object> stack = new ArrayList<>();
        int pc = 0;
        while (pc < code.length) {
            if (++steps > MAX_STEPS) throw new Abort("step limit");
            AbstractInsnNode insn = code[pc];
            int op = insn.getOpcode();
            pc++;
            if (op < 0) continue;
            switch (op) {
                case Opcodes.NOP -> {}
                case Opcodes.ACONST_NULL -> stack.add(NULL);
                case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
                        Opcodes.ICONST_4, Opcodes.ICONST_5 -> stack.add(op - Opcodes.ICONST_0);
                case Opcodes.BIPUSH, Opcodes.SIPUSH -> stack.add(((IntInsnNode) insn).operand);
                case Opcodes.LDC -> {
                    Object cst = ((LdcInsnNode) insn).cst;
                    stack.add(cst instanceof String || cst instanceof Integer ? cst : UNKNOWN);
                }
                case Opcodes.ALOAD, Opcodes.ILOAD -> {
                    Object v = vars[((VarInsnNode) insn).var];
                    stack.add(v == null ? UNKNOWN : v);
                }
                case Opcodes.ASTORE, Opcodes.ISTORE -> vars[((VarInsnNode) insn).var] = pop(stack);
                case Opcodes.POP -> pop(stack);
                case Opcodes.DUP -> {
                    Object top = pop(stack);
                    stack.add(top);
                    stack.add(top);
                }
                case Opcodes.CHECKCAST -> {}
                case Opcodes.NEW -> stack.add(new Pending());
                case Opcodes.GETSTATIC -> stack.add(getStatic((FieldInsnNode) insn));
                case Opcodes.PUTSTATIC -> {
                    FieldInsnNode f = (FieldInsnNode) insn;
                    statics.put(f.owner + "." + f.name, pop(stack));
                }
                case Opcodes.GOTO -> pc = labels.get(((JumpInsnNode) insn).label);
                case Opcodes.IFEQ, Opcodes.IFNE -> {
                    Object v = known(pop(stack));
                    if (!(v instanceof Integer i)) throw new Abort("non-int branch");
                    if ((op == Opcodes.IFEQ) == (i == 0)) pc = labels.get(((JumpInsnNode) insn).label);
                }
                case Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                    Object v = resolve(pop(stack));
                    if (v == UNKNOWN) throw new Abort("unknown null check");
                    if ((op == Opcodes.IFNULL) == (v == NULL)) pc = labels.get(((JumpInsnNode) insn).label);
                }
                case Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> {
                    Object b = resolve(pop(stack));
                    Object a = resolve(pop(stack));
                    if (a == UNKNOWN || b == UNKNOWN || !comparable(a) || !comparable(b)) {
                        throw new Abort("unknown reference comparison");
                    }
                    if ((op == Opcodes.IF_ACMPEQ) == a.equals(b)) pc = labels.get(((JumpInsnNode) insn).label);
                }
                case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE -> {
                    Object b = known(pop(stack));
                    Object a = known(pop(stack));
                    if (!(a instanceof Integer x) || !(b instanceof Integer y)) throw new Abort("non-int branch");
                    if ((op == Opcodes.IF_ICMPEQ) == x.equals(y)) pc = labels.get(((JumpInsnNode) insn).label);
                }
                case Opcodes.RETURN -> {
                    return UNKNOWN;
                }
                case Opcodes.ARETURN, Opcodes.IRETURN -> {
                    return resolve(pop(stack));
                }
                case Opcodes.INVOKESTATIC, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE ->
                    invoke(owner, method, (MethodInsnNode) insn, stack, depth);
                case Opcodes.INVOKEDYNAMIC -> invokeDynamic((InvokeDynamicInsnNode) insn, stack);
                default -> throw new Abort("unsupported opcode " + op);
            }
        }
        return UNKNOWN;
    }

    private static Object known(Object v) {
        return v;
    }

    private static boolean comparable(Object v) {
        return v == NULL || v instanceof EnumRef;
    }

    private static Object pop(ArrayList<Object> stack) {
        if (stack.isEmpty()) throw new Abort("stack underflow");
        return stack.remove(stack.size() - 1);
    }

    private static Object resolve(Object v) {
        return v instanceof Pending p ? p.value : v;
    }

    private Object getStatic(FieldInsnNode f) {
        if (f.owner.equals(ITEM_DISPLAY_CONTEXT) && f.desc.equals("L" + ITEM_DISPLAY_CONTEXT + ";")) {
            return new EnumRef(f.name);
        }
        if (f.owner.equals("java/util/Locale") && f.name.equals("ROOT")) return LOCALE_ROOT;
        Object written = statics.get(f.owner + "." + f.name);
        if (written != null) return written;
        ClassNode node = load(f.owner);
        if (node != null) {
            for (FieldNode field : node.fields) {
                if (field.name.equals(f.name) && field.desc.equals(f.desc) && (field.access & Opcodes.ACC_STATIC) != 0
                        && (field.access & Opcodes.ACC_FINAL) != 0
                        && (field.value instanceof String || field.value instanceof Integer)) {
                    return field.value;
                }
            }
        }
        return UNKNOWN;
    }

    private void invoke(ClassNode caller, MethodNode callerMethod, MethodInsnNode call, ArrayList<Object> stack, int depth) {
        Type[] argTypes = Type.getArgumentTypes(call.desc);
        Object[] args = new Object[argTypes.length];
        for (int i = argTypes.length - 1; i >= 0; i--) args[i] = resolve(pop(stack));
        boolean isStatic = call.getOpcode() == Opcodes.INVOKESTATIC;
        Object receiver = isStatic ? null : pop(stack);
        Object result = UNKNOWN;
        Object receiverValue = resolve(receiver);

        if (isCreate(call)) {
            record(caller, callerMethod, args);
        } else if (call.name.equals("<init>") && call.owner.equals(RESOURCE_LOCATION) && receiver instanceof Pending p) {
            p.value = args.length == 1 ? parseRl(args[0]) : args.length == 2 ? rl(args[0], args[1]) : UNKNOWN;
            return;
        } else if (call.name.equals("<init>")) {
            if (receiver instanceof Pending p) p.value = UNKNOWN;
            return;
        } else if (isStatic && call.owner.equals(RESOURCE_LOCATION) && call.name.equals("fromNamespaceAndPath")
                && args.length == 2) {
            result = rl(args[0], args[1]);
        } else if (isStatic && call.owner.equals(RESOURCE_LOCATION) && (call.name.equals("parse")
                || call.name.equals("tryParse")) && args.length == 1) {
            result = parseRl(args[0]);
        } else if (!isStatic && call.owner.equals("java/lang/String") && receiverValue instanceof String s) {
            result = invokeString(s, call, args);
        } else if (isStatic && !call.owner.startsWith("java/")) {
            ClassNode target = load(call.owner);
            MethodNode callee = target == null ? null : findMethod(target, call.name, call.desc);
            if (callee != null && (callee.access & Opcodes.ACC_STATIC) != 0
                    && (callee.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                Object[] locals = new Object[Math.max(callee.maxLocals, argTypes.length)];
                for (int i = 0; i < args.length; i++) locals[i] = args[i];
                result = run(target, callee, locals, depth + 1);
            }
        }
        if (Type.getReturnType(call.desc).getSort() != Type.VOID) stack.add(result == null ? UNKNOWN : result);
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) return m;
        }
        return null;
    }

    private static Object invokeString(String s, MethodInsnNode call, Object[] args) {
        switch (call.name + call.desc) {
            case "toUpperCase(Ljava/util/Locale;)Ljava/lang/String;" -> {
                return args[0] == LOCALE_ROOT ? s.toUpperCase(java.util.Locale.ROOT) : UNKNOWN;
            }
            case "toLowerCase(Ljava/util/Locale;)Ljava/lang/String;" -> {
                return args[0] == LOCALE_ROOT ? s.toLowerCase(java.util.Locale.ROOT) : UNKNOWN;
            }
            case "concat(Ljava/lang/String;)Ljava/lang/String;" -> {
                return args[0] instanceof String other ? s.concat(other) : UNKNOWN;
            }
            default -> {
                return UNKNOWN;
            }
        }
    }

    private static Object parseRl(Object value) {
        if (!(value instanceof String s)) return UNKNOWN;
        int colon = s.indexOf(':');
        return colon < 0 ? new Rl("minecraft", s) : new Rl(s.substring(0, colon), s.substring(colon + 1));
    }

    private static Object rl(Object namespace, Object path) {
        return namespace instanceof String n && path instanceof String p ? new Rl(n, p) : UNKNOWN;
    }

    private void invokeDynamic(InvokeDynamicInsnNode indy, ArrayList<Object> stack) {
        Type[] argTypes = Type.getArgumentTypes(indy.desc);
        Object[] args = new Object[argTypes.length];
        for (int i = argTypes.length - 1; i >= 0; i--) args[i] = resolve(pop(stack));
        Object result = UNKNOWN;
        Handle bsm = indy.bsm;
        if (bsm.getOwner().equals("java/lang/invoke/StringConcatFactory") && bsm.getName().equals("makeConcatWithConstants")
                && indy.bsmArgs.length >= 1 && indy.bsmArgs[0] instanceof String recipe) {
            StringBuilder out = new StringBuilder();
            int arg = 0;
            int constant = 1;
            boolean ok = true;
            for (char c : recipe.toCharArray()) {
                if (c == '\u0001') {
                    Object v = arg < args.length ? args[arg++] : UNKNOWN;
                    if (v instanceof String || v instanceof Integer || v instanceof Rl) out.append(v);
                    else ok = false;
                } else if (c == '\u0002') {
                    Object v = constant < indy.bsmArgs.length ? indy.bsmArgs[constant++] : UNKNOWN;
                    out.append(v);
                } else {
                    out.append(c);
                }
            }
            if (ok) result = out.toString();
        }
        stack.add(result);
    }

    private void record(ClassNode caller, MethodNode callerMethod, Object[] args) {
        Object key = args[0];
        Object id = args[1];
        Object fallback = args[2];
        boolean fallbackOk = fallback == NULL || fallback instanceof EnumRef;
        if (!(key instanceof String keyName) || !(id instanceof Rl rl) || !fallbackOk) return;
        String fallbackName = fallback instanceof EnumRef ref ? ref.name() : null;
        entries.putIfAbsent(rl.toString(), new Entry(keyName, rl.toString(), fallbackName));
        resolvedSites.add(caller.name + "." + callerMethod.name + callerMethod.desc);
    }
}
