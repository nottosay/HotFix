package com.groovy

import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.CtNewMethod

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

public class BuildGroovy {

    /**
     * 植入字节码
     * @param resourcePath 项目源码所在目录
     * @param buildDir 项目编译class所在目录
     * @param hackBuildDir hackdex的class所在目录
     * @param patchConfigFile 修复类的配置文件
     */
    public static void process(String resourcePath, String buildDir, String hackBuildDir, File patchConfigFile) {
        List<String> invokeClass = getInvokedClass(resourcePath, patchConfigFile);
        invokeClass.each { String string ->
            insertCode(buildDir, hackBuildDir, string)
        }
    }

    /**
     * 获取调用到修复类的类集合
     * @param resourcePath 项目源码所在目录
     * @param patchConfigFile 修复类的配置文件
     * @return
     */
    private static List<String> getInvokedClass(String resourcePath, File patchConfigFile) {
        List<String> invokeClass = new ArrayList<String>();
        List<String> lines = patchConfigFile.readLines()
        for (s in lines) {
            if (s.startsWith("#")) {
                continue;
            }
            String packageName = s.substring(0, s.lastIndexOf("."));
            String className = s.substring(s.lastIndexOf(".") + 1);
            def files = new File(resourcePath).listFiles()
            getAllInvokedClass(packageName, className, files, invokeClass);
        }
        return invokeClass;
    }

    /**
     * 从包根目录逐级获取调用到修复类的类
     * @param packageName 修复类包名
     * @param className 修复类的类名
     * @param file 文件
     * @param invokeClass 调用到修复类的类集合
     */
    private static void getAllInvokedClass(String packageName, String className, File[] files, List<String> invokeClass) {
        files.each { File file ->
            if (file.isFile()) {
                def fileName = file.path.substring(file.path.indexOf("\\src\\main\\java\\") + 15, file.path.indexOf("."))
                fileName = fileName.replace("\\", ".")
                List<String> lines = file.readLines()
                for (s in lines) {
                    if ((s.contains(packageName) || s.contains(className)) && !invokeClass.contains(fileName)) {
                        invokeClass.add(fileName)
                        break;
                    }
                }
            } else {
                getAllInvokedClass(packageName, className, file.listFiles(), invokeClass);
            }
        }
    }

    /**
     * 植入字节码
     * @param buildDir 项目编译class所在目录
     * @param hackBuildDir hackdex的class所在目录
     * @param className 调用到修复类的类
     */
    private static void insertCode(String buildDir, String hackBuildDir, String className) {
        ClassPool classes = ClassPool.getDefault()
        classes.appendClassPath(buildDir)
        classes.appendClassPath(hackBuildDir)

        CtClass c = classes.getCtClass(className)
        if (c.isFrozen()) {
            c.defrost()
        }
        CtMethod mthd = CtNewMethod.make("private void toHack() {com.hackdex.HackDex.toHack();}", c);
        c.addMethod(mthd);
        c.writeFile(buildDir)
    }

    /**
     * 文件复制
     * @param originalPath 原始文件目录
     * @param targetPath 目标文件目录
     */
    public static void copyFile(String originalPath, String targetPath) {
        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            // 获取源文件和目标文件的输入输出流
            fileInputStream = new FileInputStream(originalPath);
            File targetFile = new File(targetPath);
            if (!targetFile.exists()) {
                targetFile.getParentFile().mkdirs()
                targetFile.createNewFile();
            }
            fileOutputStream = new FileOutputStream(targetFile);
            // 获取输入输出通道
            FileChannel fileChannelIn = fileInputStream.getChannel();
            FileChannel fileChannelOut = fileOutputStream.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            while (fileChannelIn.read(buffer) != -1) {
                // clear方法重设缓冲区，使它可以接受读入的数据
                buffer.clear();
                // flip方法让缓冲区可以将新读入的数据写入另一个通道
                buffer.flip();
                fileChannelOut.write(buffer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}