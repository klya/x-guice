package com.magenta.guice.bootstrap.plugins;

import com.google.inject.Injector;
import com.google.inject.Module;
import com.magenta.guice.bootstrap.model.Plugin;
import com.magenta.guice.bootstrap.model.io.xpp3.XGuicePluginXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/*
* Project: Maxifier
* Author: Aleksey Didik
* Created: 23.05.2008 10:19:35
* 
* Copyright (c) 1999-2009 Magenta Corporation Ltd. All Rights Reserved.
* Magenta Technology proprietary and confidential.
* Use is subject to license terms.
*/

public final class PluginsManager {

    private static final Logger logger = LoggerFactory.getLogger(PluginsManager.class);

    private static final String PLUGIN_INFO_PATH = "META-INF/plugin.xml";

    private static final FilenameFilter jarFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(".jar");
        }
    };

    /**
     * Loads plugins from path
     * @param injector
     * @param pluginsPath
     * @param providedCL
     * @return
     */
    public static Injector loadPlugins(Injector injector, File pluginsPath, @Nullable URLClassLoader providedCL) {
        Collection<Module> modules = loadModules(pluginsPath, providedCL);
        for (Module module : modules) {
            if (module instanceof ChildModule) {
                ChildModule childModule = (ChildModule) module;
                childModule.beforeChildInjectorCreating(injector);
                injector = injector.createChildInjector(childModule);
            } else {
                logger.warn("Old plugin! Unable to cast " + module + " to ChildModule");
            }
        }
        return injector;
    }

    /**
     * Loads modules from path
     * @param pluginsPath
     * @param providedCL
     * @return collection of loaded modules
     */
    public static Collection<Module> loadModules(File pluginsPath, @Nullable URLClassLoader providedCL) {
        checkPath(pluginsPath);
        URL[] jars = scanJars(pluginsPath);
        ClassLoader pluginsCL;
        if (providedCL == null) {
            ClassLoader baseCL = PluginsManager.class.getClassLoader();
            pluginsCL = new URLClassLoader(jars, baseCL);
        } else {
            addJarsToClassLoader(providedCL, jars);
            pluginsCL = providedCL;
        }
        Collection<Plugin> plugins = scan(pluginsPath);
        Collection<Module> modules = new ArrayList<Module>();
        for (Plugin plugin : plugins) {
            String moduleName = plugin.getModule();
            try {
                modules.add((Module) pluginsCL.loadClass(moduleName).newInstance());
                logger.info("Plugin {} has been loaded.", plugin.getName());
            } catch (InstantiationException e) {
                logger.warn("Unable to instantiate module " + moduleName + " of plugin " + plugin.getName(), e);
            } catch (IllegalAccessException e) {
                logger.warn("Unable to instantiate module " + moduleName + " of plugin " + plugin.getName(), e);
            } catch (ClassCastException e) {
                logger.warn("Unable to cast " + moduleName + " to com.google.inject.Module " + plugin.getName(), e);
            } catch (ClassNotFoundException e) {
                logger.warn("Module class " + moduleName + " of plugin " + plugin.getName() + " is not found into classpath.", e);
            }
        }
        return modules;
    }

    /**
     * Loads modules from path by their class names
     * @param classesPath path to load modules
     * @param classNames list of class names
     * @return collection of loaded modules
     */
    public static Collection<Module> loadModulesByClassNames(File classesPath, String[] classNames) {
        checkPath(classesPath);
        ClassLoader baseCL = PluginsManager.class.getClassLoader();
        ClassLoader pluginsCL = new URLClassLoader(classPaths(classesPath), baseCL);
        Collection<Module> modules = new ArrayList<Module>();

        for (String className : classNames) {
            try {
                modules.add((Module)pluginsCL.loadClass(className).newInstance());
                logger.info("Class {} has been loaded.", className);
            } catch (InstantiationException e) {
                logger.warn("Unable to instantiate module " + className, e);
            } catch (IllegalAccessException e) {
                logger.warn("Unable to instantiate module " + className, e);
            } catch (ClassCastException e) {
                logger.warn("Unable to cast " + className + " to com.google.inject.Module", e);
            } catch (ClassNotFoundException e) {
                logger.warn("Class " + className + " is not found into " + classesPath + ".", e);
            }
        }

        return modules;
    }

    /**
     * Converts path to URLs array
     * @param path
     * @return array of URLs
     */
    private static URL[] classPaths(File path) {
        URL[] urls;

        try {
            urls = new URL[]{path.toURI().toURL()};
        } catch(MalformedURLException e) {
            throw new RuntimeException("Something wrong in filesystem" +
                    " if available file path can't converted to URL", e);
        }

        return urls;
    }

    /**
     * Adds JARs to provided class loader
     * @param providedCL
     * @param jars
     */
    private static void addJarsToClassLoader(URLClassLoader providedCL, URL[] jars) {
        Class urlClass = URLClassLoader.class;
        Method method;
        try {
            method = urlClass.getDeclaredMethod("addURL", new Class[]{URL.class});
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        method.setAccessible(true);
        for (URL jar : jars) {
            try {
                method.invoke(providedCL, jar);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Converts JAR files in path to array of URLs
     * @param pluginsPath
     * @return array of URLs
     */
    private static URL[] scanJars(File pluginsPath) {
        //prepare jars URL
        File[] jarFiles = pluginsPath.listFiles(jarFilter);
        URL[] urls = new URL[jarFiles.length];
        int i = 0;
        for (File jarFile : jarFiles) {
            try {
                urls[i++] = jarFile.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException("Something wrong in filesystem" +
                        " if available file path can't converted to URL", e);
            }
        }
        return urls;
    }

    /**
     * Scans a directory for plugins
     * @param pluginsPath
     * @return collection of found plugins
     */
    static Collection<Plugin> scan(File pluginsPath) {
        File[] jarFiles = pluginsPath.listFiles(jarFilter);
        Collection<Plugin> plugins = new HashSet<Plugin>();
        XGuicePluginXpp3Reader reader = new XGuicePluginXpp3Reader();
        for (File jarFile : jarFiles) {
            try {
                URL url = jarFile.toURI().toURL();
                ClassLoader jarClassloader = new URLClassLoader(new URL[]{url}, null);
                InputStream pluginXmlStream = jarClassloader.getResourceAsStream(PLUGIN_INFO_PATH);
                if (pluginXmlStream != null) {
                    Plugin plugin = reader.read(pluginXmlStream);
                    plugins.add(plugin);
                }
            } catch (MalformedURLException e) {
                logger.warn(String.format("Jar file URL '%s' is invalid", jarFile.toURI()), e);
            } catch (XmlPullParserException e) {
                logger.warn(String.format("plugin.xml of %s is not valid", jarFile.toURI()), e);
            } catch (IOException e) {
                logger.warn(String.format("plugin.xml of %s is not valid", jarFile.toURI()), e);
            }
        }
        return plugins;
    }

    /**
     * Checks if path exists
     * @param path
     */
    static void checkPath(File path) {
        if (!path.exists()) {
            throw new IllegalArgumentException(String.format("Path '%s' is not exists", path));
        }
        if (path.isFile()) {
            throw new IllegalArgumentException(String.format("Path '%s' must be a directory", path));
        }
    }

    private PluginsManager() {
    }

}

