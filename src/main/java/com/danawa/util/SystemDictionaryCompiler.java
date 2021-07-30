package com.danawa.util;

import com.danawa.search.analysis.dict.TagProbDictionary;
import com.danawa.search.analysis.korean.PosTagProbEntry;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class SystemDictionaryCompiler {

    public static final File RESOURCE_ROOT = new File("src/test/resources");

    public static void main(String[] args) {
        SystemDictionaryCompiler compiler = new SystemDictionaryCompiler();
        String path = "system.dict";
        if (args != null && args.length > 0) {
            path = args[0];
        }
        File outFile = new File(path);
        try {
            compiler.compileDictionary(outFile);
        } catch (Exception ex) {
            System.err.println("시스템 사전생성 실패: " + ex.getMessage());
        }
    }

    public void compileDictionary(File outFile) throws IOException {

        if(outFile.getParentFile() != null) {
            outFile.getParentFile().mkdirs();
        }
        if (!outFile.exists()) {
            outFile.createNewFile();
        }

        OutputStream out = new FileOutputStream(outFile);
        try {
            TagProbDictionary dictionary = loadTagProbDictionary();
            dictionary.writeTo(out);
        } finally {
            if (out != null) {
                out.close();
            }
        }

        System.out.println("바이너리 사전을 생성하였습니다. " + outFile.getAbsolutePath() + " (" + outFile.length() + "B)");
    }


    public final TagProbDictionary loadTagProbDictionary() {
        try {
            File dictDir = new File(RESOURCE_ROOT, com.danawa.search.analysis.dict.TagProbDictionary
                    .class.getPackage().getName().replaceAll("[.]", "/"));
            TagProbDictionary baseDict = new TagProbDictionary(true);
            loadTagProbSource(baseDict, new File(dictDir, "0.lnpr_morp.txt"));
            loadTagProbSource(baseDict, new File(dictDir, "words-prob.txt"));
            loadTagProbByFileName(baseDict, new File(dictDir, "01.N.P11.txt"));
            loadTagProbByFileName(baseDict, new File(dictDir, "02.N.MIN.txt"));
            loadTagProbByFileName(baseDict, new File(dictDir, "03.N.LOW.txt"));
            loadTagProbByFileName(baseDict, new File(dictDir, "04.N.MIN.txt"));
            return baseDict;
        } catch (final Exception e) {
            System.err.println("ERROR LOADING BASE DICTIONARY : " + e.getMessage());
        }
        return null;

    }

    private void loadTagProbSource(final TagProbDictionary dictionary, final File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            for (String line; (line = reader.readLine()) != null; ) {
                dictionary.addSourceEntry(line);
            }
        } catch (final Exception e) {
            System.err.println(e.getMessage());
        } finally {
            try {
                reader.close();
            } catch (final Exception ignore) {
            }
        }
    }

    private void loadTagProbByFileName(TagProbDictionary dictionary, File file) {
        BufferedReader reader = null;
        try {
            Set<CharSequence> entrySet = new HashSet<>();
            String fileName = file.getName();
            String[] parts = fileName.split("\\.");
            String posName = parts[1];
            String probType = parts[2];
            PosTagProbEntry.PosTag posTag = null;
            try {
                posTag = PosTagProbEntry.PosTag.valueOf(posName);
            } catch (Exception e) {
                System.err.println("Undefined pos tag = " + posName);
                throw e;
            }
            double prob = PosTagProbEntry.TagProb.getProb(probType);

            reader = new BufferedReader(new FileReader(file));
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.startsWith("#") || line.startsWith("//") || line.length() == 0) {
                    continue;
                }
                CharVector cv = new CharVector(line);
                if (dictionary.ignoreCase()) {
                    cv.ignoreCase();
                }
                entrySet.add(cv);
            }
            dictionary.appendPosTagEntry(entrySet, posTag, prob);
        } catch (final Exception e) {
            System.err.println(e.getMessage());
        } finally {
            try {
                reader.close();
            } catch (final Exception ignore) {
            }
        }
    }
}
