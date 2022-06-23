/*
  Copyright 2021 The Vine Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package io.github.bynix.vine.kit;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author bynix
 */
public class LicenseKit {

    public static void main(String[] args) throws Exception {
        try (InputStream in = LicenseKit.class.getClassLoader().getResourceAsStream("license-header.txt")) {
            Objects.requireNonNull(in);
            byte[] header = in.readAllBytes();
            String projectDir = System.getProperty("user.dir");
            List<File> files = new ArrayList<>();
            scanJavaFiles(new File(projectDir), files);

            files.parallelStream().forEach(file -> {
                try {
                    byte[] source = Files.readAllBytes(file.toPath());
                    if (source[0] != (byte) '/') {
                        try (OutputStream out = new FileOutputStream(file)) {
                            out.write(header);
                            out.write(source);
                            out.flush();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public static void scanJavaFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanJavaFiles(file, list);
            } else if (file.getName().endsWith(".java")) {
                list.add(file);
            }
        }
    }

}
