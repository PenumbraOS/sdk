const classLoaders = Java.enumerateClassLoadersSync().map(
  Java.ClassFactory.get
);

export const loadClass = (name: string): any => {
  for (const classLoader of classLoaders) {
    try {
      return classLoader.use(name);
    } catch {}
  }
  return undefined;
};

export const chooseLiveClasses = (name: string): any[] => {
  const instances: any[] = [];
  for (const classLoader of classLoaders) {
    try {
      classLoader.choose(name, {
        onMatch: (instance: any) => {
          instances.push(instance);
        },
      });
    } catch {}
  }
  return instances;
};
