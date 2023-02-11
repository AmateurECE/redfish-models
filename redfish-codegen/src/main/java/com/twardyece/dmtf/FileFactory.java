package com.twardyece.dmtf;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.twardyece.dmtf.text.SnakeCaseName;
import io.swagger.v3.oas.models.media.Schema;

public class FileFactory {
    private MustacheFactory factory;
    private Mustache modelTemplate;
    private Mustache moduleTemplate;
    private String modelsBasePath;

    public FileFactory(MustacheFactory factory, String modelsBasePath) {
        this.factory = factory;
        this.modelTemplate = factory.compile("templates/model.mustache");
        this.moduleTemplate = factory.compile("templates/module.mustache");
        this.modelsBasePath = modelsBasePath;
    }

    public ModelFile makeModelFile(SnakeCaseName[] module, Schema schema) {
        return new ModelFile(module, schema, this.modelTemplate, modelsBasePath);
    }

    public ModuleFile makeModuleFile(String path) {
        return new ModuleFile(path, this.moduleTemplate);
    }
}