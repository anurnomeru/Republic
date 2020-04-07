package ink.anur.inject;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by Anur IjuoKaruKas on 2020/2/24
 */
public class Utils {
    //从包路径下扫描
    public static Set<Class> getClasses(String packagePath) {
        Set<Class> res = new HashSet<>();
        String path = packagePath.replace(".", "/");
        URL url = Thread.currentThread().getContextClassLoader().getResource(path);
        if (url == null) {
            System.out.println(packagePath + " is not exit");
            return res;
        }
        String protocol = url.getProtocol();
        if ("jar".equalsIgnoreCase(protocol)) {
            try {
                res.addAll(getJarClasses(url, packagePath));
            } catch (IOException e) {
                e.printStackTrace();
                return res;
            }
        } else if ("file".equalsIgnoreCase(protocol)) {
            res.addAll(getFileClasses(url, packagePath));
        }
        return res;
    }
    //获取file路径下的class文件
    private static Set<Class> getFileClasses(URL url, String packagePath) {
        Set<Class> res = new HashSet<>();
        String filePath = url.getFile();
        File dir = new File(filePath);
        String[] list = dir.list();
        if (list == null) return res;
        for (String classPath : list) {
            if (classPath.endsWith(".class")) {
                classPath = classPath.replace(".class", "");
                try {
                    Class<?> aClass = Class.forName(packagePath + "." + classPath);
                    res.add(aClass);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                res.addAll(getClasses(packagePath + "." + classPath));
            }
        }
        return res;
    }
    //使用JarURLConnection类获取路径下的所有类
    private static Set<Class> getJarClasses(URL url, String packagePath) throws IOException {
        Set<Class> res = new HashSet<>();
        JarURLConnection conn = (JarURLConnection) url.openConnection();
        if (conn != null) {
            JarFile jarFile = conn.getJarFile();
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                String name = jarEntry.getName();
                if (name.contains(".class") && name.replaceAll("/", ".").startsWith(packagePath)) {
                    String className = name.substring(0, name.lastIndexOf(".")).replace("/", ".");
                    try {
                        Class clazz = Class.forName(className);
                        res.add(clazz);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return res;
    }


    public static void main(String[] args) {
        Set<Class> classes = Utils.getClasses("ink.anur");
        classes.addAll(Utils.getClasses("ink.anur"));
        for (Class clazz : classes) {
            System.out.println(clazz.getName());
        }
    }
}
