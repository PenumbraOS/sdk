const android_log_write = new NativeFunction(
  Module.getExportByName(null, "__android_log_write"),
  "int",
  ["int", "pointer", "pointer"]
);

export const log = (value: string): void => {
  const tag = Memory.allocUtf8String("Frida");
  const string = Memory.allocUtf8String(value);
  android_log_write(3, tag, string);
};
