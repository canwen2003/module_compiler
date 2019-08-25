package com.blued.android.module.compiler;

import com.blued.android.module.interfaces.annotation.RouterService;
import com.blued.android.module.interfaces.interfaces.Const;
import com.blued.android.module.interfaces.service.ServiceImpl;
import com.google.auto.service.AutoService;

import com.sun.tools.javac.code.Symbol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;

@AutoService(Processor.class)
public class ServiceAnnotationProcessor extends BaseProcessor {

    /**
     * interfaceClass --> Entity
     */
    private final HashMap<String, Entity> mEntityMap = new HashMap<>();
    private String mHash = null;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        log("zhaozy:process");
        if (env.processingOver()) {
            generateInitClass();
        } else {
            processAnnotations(env);
        }
        return true;
    }

    private void processAnnotations(RoundEnvironment env) {
        for (Element element : env.getElementsAnnotatedWith(RouterService.class)) {
            if (!(element instanceof Symbol.ClassSymbol)) {
                continue;
            }

            Symbol.ClassSymbol cls = (Symbol.ClassSymbol) element;
            if (mHash == null) {
                mHash = hash(cls.className());
            }

            RouterService service = cls.getAnnotation(RouterService.class);
            if (service == null) {
                continue;
            }

            List<? extends TypeMirror> typeMirrors = getInterface(service);
            String[] keys = service.key();

            String implementationName = cls.className();
            boolean singleton = service.singleton();

            if (typeMirrors != null && !typeMirrors.isEmpty()) {
                for (TypeMirror mirror : typeMirrors) {
                    if (mirror == null) {
                        continue;
                    }
                    if (!isConcreteSubType(cls, mirror)) {
                        String msg = cls.className() + "没有实现注解" + RouterService.class.getName()
                                + "标注的接口" + mirror.toString();
                        throw new RuntimeException(msg);
                    }
                    String interfaceName = getClassName(mirror);

                    Entity entity = mEntityMap.get(interfaceName);
                    if (entity == null) {
                        entity = new Entity(interfaceName);
                        mEntityMap.put(interfaceName, entity);
                    }
                    if (keys.length > 0) {
                        for (String key : keys) {
                            if (key.contains(":")) {
                                String msg = String.format("%s: 注解%s的key参数不可包含冒号",
                                        implementationName, RouterService.class.getName());
                                throw new RuntimeException(msg);
                            }
                            entity.put(key, implementationName, singleton);
                        }
                    } else {
                        entity.put(null, implementationName, singleton);
                    }
                }
            }
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        //支持的java版本
        return SourceVersion.RELEASE_7;
    }

    private void generateInitClass() {
        if (mEntityMap.isEmpty()) {
            log("zhaozy:process: 3");
            return;
        }

        if ( mHash == null) {
            log("zhaozy:process: 4");
            return;
        }

        log("zhaozy:process: 5");
        ServiceInitClassBuilder generator = new ServiceInitClassBuilder("ServiceInit" + Const.SPLITTER + mHash);

        for (Map.Entry<String, Entity> entry : mEntityMap.entrySet()) {
            for (ServiceImpl service : entry.getValue().getMap().values()) {
                log("zhaozy:process: 6 "+entry.getKey());
                generator.put(entry.getKey(), service.getKey(), service.getImplementation(), service.isSingleton());
            }
        }
        generator.build();
    }

    private static List<? extends TypeMirror> getInterface(RouterService service) {
        try {
            service.interfaces();
        } catch (MirroredTypesException mte) {
            return mte.getTypeMirrors();
        }
        return null;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {

        //支持的注解
        /*Set<String> annotations = new LinkedHashSet<>();
        annotations.add(RouterService.class.getCanonicalName());
        return annotations;*/
        return new HashSet<>(Collections.singletonList(RouterService.class.getName()));
    }

    public static class Entity {

        private final String mInterfaceName;

        private final Map<String, ServiceImpl> mMap = new HashMap<>();

        public Entity(String interfaceName) {
            mInterfaceName = interfaceName;
        }

        public Map<String, ServiceImpl> getMap() {
            return mMap;
        }

        public void put(String key, String implementationName, boolean singleton) {
            if (implementationName == null) {
                return;
            }
            ServiceImpl impl = new ServiceImpl(key, implementationName, singleton);
            ServiceImpl prev = mMap.put(impl.getKey(), impl);
            String errorMsg = ServiceImpl.checkConflict(mInterfaceName, prev, impl);
            if (errorMsg != null) {
                throw new RuntimeException(errorMsg);
            }
        }



        public List<String> getContents() {
            List<String> list = new ArrayList<>();
            for (ServiceImpl impl : mMap.values()) {
                list.add(impl.toConfig());
            }
            return list;
        }
    }
}
