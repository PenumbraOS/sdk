// Frida type definitions for TypeScript
declare const Java: {
  available: boolean;
  enumerateClassLoadersSync(): any[];
  ClassFactory: {
    get(loader: any): any;
  };
  array(type: any, value: unknown[]): any;
  use(className: string): any;
  cast(obj: any, cls: any): any;
  scheduleOnMainThread(fn: () => void): void;
};

declare const Memory: {
  allocUtf8String(str: string): NativePointer;
};

declare const Module: {
  getExportByName(moduleName: string | null, exportName: string): NativePointer;
};

declare const NativeFunction: new (
  address: NativePointer,
  retType: string,
  argTypes: string[]
) => (...args: any[]) => any;

declare interface NativePointer {
  // Add NativePointer methods as needed
}

declare const log: (message: string) => void;
declare const console: Console;

// Global variables that we'll use in the script
declare const global: {
  setJavaCallbackHandler: (handler: any) => void;
  registerFridaCallback: (callbackObject: any) => void;
};
