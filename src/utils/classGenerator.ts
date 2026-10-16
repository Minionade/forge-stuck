/**
 * Generates valid Java 21 (.class) bytecode binary structures.
 * Magic: 0xCAFEBABE, Major Version: 65 (Java 21).
 *
 * Extended: supports adding simple instance methods that return null (useful for interface stubs)
 */
export function generateClassBytes(
  className: string,
  superName: string = 'java/lang/Object',
  interfaces: string[] = []
): Uint8Array {
  // Constant Pool Builder
  const cpEntries: { tag: number; bytes: number[] }[] = [];

  function addUtf8(str: string): number {
    const encoder = new TextEncoder();
    const strBytes = Array.from(encoder.encode(str));
    const len = strBytes.length;
    const bytes = [(len >> 8) & 0xff, len & 0xff, ...strBytes];
    cpEntries.push({ tag: 1, bytes });
    return cpEntries.length;
  }

  function addClass(nameUtf8Idx: number): number {
    cpEntries.push({ tag: 7, bytes: [(nameUtf8Idx >> 8) & 0xff, nameUtf8Idx & 0xff] });
    return cpEntries.length;
  }

  function addNameAndType(nameIdx: number, descIdx: number): number {
    cpEntries.push({
      tag: 12,
      bytes: [(nameIdx >> 8) & 0xff, nameIdx & 0xff, (descIdx >> 8) & 0xff, descIdx & 0xff]
    });
    return cpEntries.length;
  }

  function addMethodRef(classIdx: number, natIdx: number): number {
    cpEntries.push({
      tag: 10,
      bytes: [(classIdx >> 8) & 0xff, classIdx & 0xff, (natIdx >> 8) & 0xff, natIdx & 0xff]
    });
    return cpEntries.length;
  }

  // Pre-seed constant pool
  const thisNameUtf8 = addUtf8(className);
  const thisClassIdx = addClass(thisNameUtf8);

  const superNameUtf8 = addUtf8(superName);
  const superClassIdx = addClass(superNameUtf8);

  const ifaceIndices: number[] = [];
  for (const iface of interfaces) {
    const ifaceUtf8 = addUtf8(iface);
    ifaceIndices.push(addClass(ifaceUtf8));
  }

  // Prepare method entries collection (we'll always add <init>, optionally more)
  type MethodEntry = { nameIdx: number; descIdx: number; codeBytes: number[]; maxStack: number; maxLocals: number };
  const methods: MethodEntry[] = [];

  // <init> method
  const initNameUtf8 = addUtf8('<init>');
  const initDescUtf8 = addUtf8('()V');

  // Build <init> code: aload_0; invokespecial super.<init>; return
  const superInitNat = addNameAndType(initNameUtf8, initDescUtf8);
  const superInitMethodRef = addMethodRef(superClassIdx, superInitNat);

  const initCodeBytes = [
    0x2a, // aload_0
    0xb7, (superInitMethodRef >> 8) & 0xff, superInitMethodRef & 0xff, // invokespecial
    0xb1 // return
  ];

  methods.push({ nameIdx: initNameUtf8, descIdx: initDescUtf8, codeBytes: initCodeBytes, maxStack: 1, maxLocals: 1 });

  // If implementing IModFileReader, add a stubbed instance method "read" with correct descriptor
  if (interfaces.includes('net/neoforged/neoforgespi/locating/IModFileReader')) {
    const readNameUtf8 = addUtf8('read');
    const readDescUtf8 = addUtf8('(Lcpw/mods/jarhandling/JarContents;Lnet/neoforged/neoforgespi/locating/ModFileDiscoveryAttributes;)Lnet/neoforged/neoforgespi/locating/IModFile;');

    // Method body: aconst_null; areturn
    const readCodeBytes = [0x01, 0xb0];
    // instance method: locals: 0=this,1=JarContents,2=ModFileDiscoveryAttributes => max_locals = 3
    methods.push({ nameIdx: readNameUtf8, descIdx: readDescUtf8, codeBytes: readCodeBytes, maxStack: 1, maxLocals: 3 });
  }

  // Assemble full byte array
  const buffer: number[] = [];

  // Magic
  buffer.push(0xca, 0xfe, 0xba, 0xbe);
  // Minor: 0
  buffer.push(0x00, 0x00);
  // Major: 65 (Java 21)
  buffer.push(0x00, 0x41);

  // Constant pool count = cpEntries.length + 1
  const cpCount = cpEntries.length + 1;
  buffer.push((cpCount >> 8) & 0xff, cpCount & 0xff);

  for (const entry of cpEntries) {
    buffer.push(entry.tag);
    buffer.push(...entry.bytes);
  }

  // Access flags: ACC_PUBLIC (0x0001) | ACC_SUPER (0x0020)
  buffer.push(0x00, 0x21);

  // this_class
  buffer.push((thisClassIdx >> 8) & 0xff, thisClassIdx & 0xff);

  // super_class
  buffer.push((superClassIdx >> 8) & 0xff, superClassIdx & 0xff);

  // interfaces
  buffer.push((ifaceIndices.length >> 8) & 0xff, ifaceIndices.length & 0xff);
  for (const idx of ifaceIndices) {
    buffer.push((idx >> 8) & 0xff, idx & 0xff);
  }

  // fields_count: 0
  buffer.push(0x00, 0x00);

  // methods_count: methods.length
  buffer.push((methods.length >> 8) & 0xff, methods.length & 0xff);

  // For each method, emit method_info
  for (const m of methods) {
    // access_flags: public (0x0001)
    buffer.push(0x00, 0x01);
    // name_index
    buffer.push((m.nameIdx >> 8) & 0xff, m.nameIdx & 0xff);
    // descriptor_index
    buffer.push((m.descIdx >> 8) & 0xff, m.descIdx & 0xff);

    // Build Code attribute
    const codeLen = m.codeBytes.length;
    const attributeLength = 12 + codeLen; // as per spec: max_stack(2)+max_locals(2)+code_length(4)+code_len+exception(2)+attrs(2)

    // attributes_count = 1 (Code)
    buffer.push(0x00, 0x01);

    // attribute_name_index -> code attribute name must be in cp; find or add
    // We added 'Code' earlier for init, but not guaranteed here. Ensure it exists.
    let codeAttrIndex = -1;
    // find existing utf8 'Code'
    for (let i = 0; i < cpEntries.length; i++) {
      const e = cpEntries[i];
      if (e.tag === 1) {
        // bytes start with length two bytes then utf8 bytes
        const len = (e.bytes[0] << 8) | e.bytes[1];
        const strBytes = e.bytes.slice(2);
        const s = new TextDecoder().decode(new Uint8Array(strBytes));
        if (s === 'Code') {
          codeAttrIndex = i + 1; // constant pool index
          break;
        }
      }
    }
    if (codeAttrIndex === -1) {
      // add utf8 'Code'
      codeAttrIndex = addUtf8('Code');
    }

    // attribute_name_index
    buffer.push((codeAttrIndex >> 8) & 0xff, codeAttrIndex & 0xff);
    // attribute_length (u4)
    buffer.push((attributeLength >> 24) & 0xff, (attributeLength >> 16) & 0xff, (attributeLength >> 8) & 0xff, attributeLength & 0xff);

    // max_stack (u2)
    buffer.push((m.maxStack >> 8) & 0xff, m.maxStack & 0xff);
    // max_locals (u2)
    buffer.push((m.maxLocals >> 8) & 0xff, m.maxLocals & 0xff);
    // code_length (u4)
    buffer.push((codeLen >> 24) & 0xff, (codeLen >> 16) & 0xff, (codeLen >> 8) & 0xff, codeLen & 0xff);
    // code bytes
    buffer.push(...m.codeBytes);
    // exception_table_length (u2) = 0
    buffer.push(0x00, 0x00);
    // attributes_count (u2) = 0
    buffer.push(0x00, 0x00);
  }

  // class attributes_count: 0
  buffer.push(0x00, 0x00);

  return new Uint8Array(buffer);
}
