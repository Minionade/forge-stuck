import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * Regenerates src/main/resources/forgestuck/srg-members.properties.
 *
 * Inputs: Mojang 1.20.1 client mappings (client.txt), MCPConfig 1.20.1 config/joined.tsrg (obf -> SRG) and the
 * NeoForge 1.21.1 merged Minecraft jar. An SRG name is emitted only when its Mojang-named declaring class exists in
 * 1.21.1 and declares a member with the same Mojang name (methods additionally need a matching parameter count).
 *
 * java -cp asm.jar;asm-tree.jar tools/SrgTableGenerator.java client.txt joined.tsrg minecraft-merged.jar out.properties
 */
public class SrgTableGenerator {
    public static void main(String[] a) throws Exception {
        Map<String, String> mojToObfClass = new HashMap<>();
        Map<String, String> obfToMojClass = new HashMap<>();
        Map<String, List<String[]>> fields = new HashMap<>();
        Map<String, List<String[]>> methods = new HashMap<>();
        String cur = null;
        for (String line : Files.readAllLines(Path.of(a[0]))) {
            if (line.startsWith("#") || line.isBlank()) continue;
            if (!line.startsWith(" ")) {
                String[] p = line.split(" -> ");
                String moj = p[0].replace('.', '/');
                cur = p[1].substring(0, p[1].length() - 1).replace('.', '/');
                mojToObfClass.put(moj, cur);
                obfToMojClass.put(cur, moj);
                continue;
            }
            String[] p = line.trim().split(" -> ");
            String obf = p[1];
            String left = p[0].replaceFirst("^\\d+:\\d+:", "").replaceFirst(":\\d+:\\d+$", "");
            int sp = left.indexOf(' ');
            String type = left.substring(0, sp);
            String rest = left.substring(sp + 1);
            if (rest.contains("(")) {
                String name = rest.substring(0, rest.indexOf('('));
                String args = rest.substring(rest.indexOf('(') + 1, rest.length() - 1);
                methods.computeIfAbsent(cur, k -> new ArrayList<>()).add(new String[] {name, obf, type, args});
            } else {
                fields.computeIfAbsent(cur, k -> new ArrayList<>()).add(new String[] {rest, obf, type});
            }
        }

        Map<String, ClassNode> target = new HashMap<>();
        try (ZipFile z = new ZipFile(a[2])) {
            var en = z.entries();
            while (en.hasMoreElements()) {
                var e = en.nextElement();
                if (!e.getName().endsWith(".class") || !(e.getName().startsWith("net/minecraft/") || e.getName().startsWith("com/mojang/"))) continue;
                ClassNode cn = new ClassNode();
                new ClassReader(z.getInputStream(e)).accept(cn, ClassReader.SKIP_CODE);
                target.put(cn.name, cn);
            }
        }

        Map<String, String> out = new TreeMap<>();
        int missingClass = 0, missingMember = 0;
        String obfClass = null;
        for (String line : Files.readAllLines(Path.of(a[1]))) {
            if (line.startsWith("tsrg2")) continue;
            if (!line.startsWith("\t")) {
                obfClass = line.split(" ")[0];
                continue;
            }
            if (line.startsWith("\t\t")) continue;
            String[] p = line.trim().split(" ");
            String srg;
            String obf = p[0];
            String desc = null;
            if (p.length == 3) {
                srg = p[1];
            } else if (p.length == 4) {
                desc = p[1];
                srg = p[2];
            } else {
                continue;
            }
            if (!srg.startsWith("f_") && !srg.startsWith("m_")) continue;
            String mojClass = obfToMojClass.get(obfClass);
            if (mojClass == null) continue;
            ClassNode tc = target.get(mojClass);
            if (tc == null) {
                missingClass++;
                continue;
            }
            String moj = null;
            if (desc == null) {
                for (String[] f : fields.getOrDefault(obfClass, List.of())) if (f[1].equals(obf)) moj = f[0];
                if (moj != null) {
                    String n = moj;
                    if (tc.fields.stream().noneMatch(f -> f.name.equals(n))) moj = null;
                }
            } else {
                for (String[] m : methods.getOrDefault(obfClass, List.of())) {
                    if (m[1].equals(obf) && obfDesc(m[2], m[3], mojToObfClass).equals(desc)) moj = m[0];
                }
                if (moj != null) {
                    int argc = argCount(desc);
                    String n = moj;
                    if (tc.methods.stream().noneMatch(m -> m.name.equals(n) && argCount(m.desc) == argc)) moj = null;
                }
            }
            if (moj == null || moj.startsWith("lambda$") || moj.equals(srg)) {
                missingMember++;
                continue;
            }
            String prev = out.put(srg, moj);
            if (prev != null && !prev.equals(moj)) System.err.println("conflict " + srg + " " + prev + " " + moj);
        }
        System.err.println("mapped=" + out.size() + " classMissingIn1.21=" + missingClass + " memberMissing=" + missingMember);
        try (BufferedWriter w = Files.newBufferedWriter(Path.of(a[3]), StandardCharsets.UTF_8)) {
            w.write("# Legacy SRG member name -> Mojang runtime name (1.20.1 -> 1.21.1).\n");
            w.write("# Generated by tools/SrgTableGenerator.java from Mojang 1.20.1 client mappings + MCPConfig 1.20.1 joined.tsrg;\n");
            w.write("# an entry exists only if the declaring class and a same-named member exist in NeoForge 1.21.1.\n");
            for (var e : out.entrySet()) w.write(e.getKey() + "=" + e.getValue() + "\n");
        }
    }

    static int argCount(String desc) {
        int n = 0;
        int i = 1;
        while (desc.charAt(i) != ')') {
            while (desc.charAt(i) == '[') i++;
            if (desc.charAt(i) == 'L') i = desc.indexOf(';', i);
            i++;
            n++;
        }
        return n;
    }

    static String obfDesc(String ret, String args, Map<String, String> cls) {
        StringBuilder sb = new StringBuilder("(");
        if (!args.isEmpty()) for (String s : args.split(",")) sb.append(typeDesc(s, cls));
        return sb.append(')').append(typeDesc(ret, cls)).toString();
    }

    static String typeDesc(String t, Map<String, String> cls) {
        int dims = 0;
        while (t.endsWith("[]")) {
            dims++;
            t = t.substring(0, t.length() - 2);
        }
        String base = switch (t) {
            case "int" -> "I";
            case "long" -> "J";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "float" -> "F";
            case "double" -> "D";
            case "void" -> "V";
            default -> {
                String in = t.replace('.', '/');
                yield "L" + cls.getOrDefault(in, in) + ";";
            }
        };
        return "[".repeat(dims) + base;
    }
}
