import typescript from "@rollup/plugin-typescript";

export default {
  input: "src/index.ts",
  output: {
    file: "dist/index.js",
    format: "iife",
    sourcemap: false,
  },
  plugins: [
    typescript({
      tsconfig: false,
      compilerOptions: {
        target: "ES2018",
        module: "ESNext",
        lib: ["ES2018"],
        strict: true,
        esModuleInterop: true,
        skipLibCheck: true,
        forceConsistentCasingInFileNames: true,
        moduleResolution: "node",
        resolveJsonModule: true,
        typeRoots: ["./types"],
        types: [],
      },
      include: ["src/**/*", "types/index.d.ts"],
      noEmitOnError: false,
    }),
  ],
  external: [],
};
