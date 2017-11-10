package com.pixolut.tfb_insight.model;

import act.db.morphia.MorphiaAdaptiveRecord;
import act.db.morphia.MorphiaDao;
import act.util.Stateless;
import com.alibaba.fastjson.JSON;
import com.pixolut.tfb_insight.util.ColorCaculator;
import com.pixolut.tfb_insight.util.DensityCalculator;
import org.mongodb.morphia.annotations.Entity;
import org.osgl.$;
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.S;

import java.io.File;
import java.util.*;

@Entity("prj")
public class Project extends MorphiaAdaptiveRecord<Project> {

    public enum Technology {
        JVM("Java", "Kotlin", "Scala", "Closure", "Groovy"), DOT_NET("C#"), OTHER;
        private Set<String> languages;
        Technology(String ... languages) {
            this.languages = C.setOf(languages);
        }

        public boolean testLanguage(String language) {
            if (languages.contains(language)) {
                return true;
            }
            if (languages.isEmpty()) {
                return !JVM.testLanguage(language) && !DOT_NET.testLanguage(language);
            }
            return false;
        }
    }

    public String framework;
    public Set<String> db;
    public List<Test> tests;
    public Test.Classification classification;
    public Technology technology;
    public int loc;
    public int testCount;
    public String language;
    public String projectRoot;
    public String color;
    public Float density;

    public Project(BenchmarkConfig config, File projectRoot) {
        this.projectRoot = S.afterFirst(projectRoot.getAbsolutePath(), "frameworks");
        this.init(config);
    }

    public String getLabel() {
        return S.concat("[", language, "] ", framework);
    }

    public void calculateDensity() {
        this.density = DensityCalculator.calculate(this);
    }

    public boolean hasEffectiveTests() {
        return !tests.isEmpty();
    }

    public $.T2<Test, Test.Result> bestOf(TestType testType) {
        Test.Result theResult = null;
        Test theTest = null;
        for (Test test : tests) {
            Test.Result testResult = test.bestResult.get(testType);
            if (null == testResult) {
                continue;
            }
            if (null == theResult) {
                theResult = testResult;
                theTest = test;
            } else if (testResult.isBetterThan(theResult)) {
                theResult = testResult;
                theTest = test;
            }
        }
        return $.T2(theTest, theResult);
    }

    private void init(BenchmarkConfig config) {
        this.framework = config.framework;
        this.tests = new ArrayList<>();
        this.db = new HashSet<>();
        for (Map<String, Test> map : config.tests) {
            for (Map.Entry<String, Test> entry : map.entrySet()) {
                String testName = entry.getKey();
                Test test = entry.getValue();
                if (test.approach == Test.Approach.Stripped) {
                    // ignore stripped test
                    continue;
                }
                test.name = testName;
                this.tests.add(test);
                String database = test.database;
                if (S.notBlank(database) && !"none".equalsIgnoreCase(database)) {
                    this.db.add(database);
                }
            }
        }
        if (this.tests.isEmpty()) {
            return;
        }
        this.language = this.tests.get(0).language;
        if (null == this.language) {
            throw E.unexpected("Language not found for %s", JSON.toJSONString(config));
        }
        this.classification = this.tests.get(0).classification;
        this.technology = findTechnologyByLanguage(language);
    }

    public void updateColors() {
        this.color = ColorCaculator.colorOf(this);
        for (Test test : tests) {
            test.color = ColorCaculator.colorOf(this, test);
        }
    }

    private Technology findTechnologyByLanguage(String language) {
        for (Technology tech : Technology.values()) {
            if (tech.testLanguage(language)) {
                return tech;
            }
        }
        return Technology.OTHER;
    }

    @Stateless
    public static class Dao extends MorphiaDao<Project> {
    }


}
