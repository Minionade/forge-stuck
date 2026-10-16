/**
 * Generates valid Java 21 (.class) bytecode binary structures.
 * Magic: 0xCAFEBABE, Major Version: 65 (Java 21).
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

  // <init> method
  const initNameUtf8 = addUtf8('<init>');
  const initDescUtf8 = addUtf8('()V');
  const codeAttrUtf8 = addUtf8('Code');

  const superInitNat = addNameAndType(initNameUtf8, initDescUtf8);
  const superInitMethodRef = addMethodRef(superClassIdx, superInitNat);

  // Build Code attribute for <init>
  // Instructions: aload_0 (0x2a), invokespecial super.<init> (0xb7 + u2), return (0xb1)
  const codeBytes = [
    0x2a, // aload_0
    0xb7, (superInitMethodRef >> 8) & 0xff, superInitMethodRef & 0xff, // invokespecial
    0xb1  // return
  ];

  const codeAttrBytes = [
    (codeAttrUtf8 >> 8) & 0xff, codeAttrUtf8 & 0xff, // attribute_name_index
    0x00, 0x00, 0x00, 0x11, // attribute_length: 4 (max_stack, max_locals) + 4 (code_len) + 5 (code) + 2 (exceptions) + 2 (attrs) = 17 bytes
    0x00, 0x01, // max_stack = 1
    0x00, 0x01, // max_locals = 1
    0x00, 0x00, 0x00, 0x05, // code_length = 5
    ...codeBytes,
    0x00, 0x00, // exception_table_length = 0
    0x00, 0x00  // attributes_count = 0
  ];

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

  // methods_count: 1 (<init>)
  buffer.push(0x00, 0x01);

  // method: ACC_PUBLIC (0x0001)
  buffer.push(0x00, 0x01);
  buffer.push((initNameUtf8 >> 8) & 0xff, initNameUtf8 & 0xff);
  buffer.push((initDescUtf8 >> 8) & 0xff, initDescUtf8 & 0xff);
  // attributes_count: 1 (Code)
  buffer.push(0x00, 0x01);
  buffer.push(...codeAttrBytes);

  // class attributes_count: 0
  buffer.push(0x00, 0x00);

  return new Uint8Array(buffer);
}
