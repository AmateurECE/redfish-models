package com.twardyece.dmtf;

import com.fasterxml.jackson.databind.Module;
import io.swagger.v3.oas.models.media.Schema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public class ModelFile {
    Schema schema;
    SnakeCaseName[] module;

    public ModelFile(SnakeCaseName[] module, Schema schema) {
        this.schema = schema;
        this.module = module;

        // Ensure that the model name is PascalCase'd
        schema.setName(CaseConversion.toPascalCase(schema.getName()).toString());
    }

    public void registerModel(Map<String, ModuleFile> modules) {
        String path = "";
        for (SnakeCaseName component : this.module) {
            if (!"".equals(path)) {
                modules.get(path).addSubmodule(component);
            } else {
                path = "src/" + RustConfig.MODELS_BASE_MODULE;
            }

            path += "/" + component;
            if (!modules.containsKey(path)) {
                modules.put(path, new ModuleFile(path + RustConfig.FILE_EXTENSION));
            }
        }
    }

    public void generate() throws IOException {
        ArrayList<String> module = new ArrayList<>();
        for (SnakeCaseName component : this.module) {
            module.add(component.toString());
        }

        SnakeCaseName modelName = new SnakeCaseName(new PascalCasedName(this.schema.getName()));
        if ("".equals(modelName.toString())) {
            System.out.println("[WARN] modelName is empty for model " + this.schema.getName());
        }
        String path = "src/" + RustConfig.MODELS_BASE_MODULE + "/" + String.join("/", module) + "/"
                + modelName.toString() + RustConfig.FILE_EXTENSION;

        File modelFile = new File(path);
        File parent = modelFile.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        modelFile.createNewFile();
    }
}
