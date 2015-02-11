package org.jboss.errai.config.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.jboss.errai.codegen.meta.MetaClass;
import org.jboss.errai.codegen.meta.MetaClassFactory;
import org.jboss.errai.codegen.meta.MetaField;
import org.jboss.errai.codegen.meta.MetaMethod;
import org.jboss.errai.codegen.meta.MetaParameter;
import org.jboss.errai.codegen.meta.impl.java.JavaReflectionField;
import org.jboss.errai.codegen.meta.impl.java.JavaReflectionMethod;
import org.jboss.errai.common.metadata.RebindUtils;
import org.jboss.errai.common.metadata.ScannerSingleton;
import org.jboss.errai.common.rebind.CacheStore;
import org.jboss.errai.common.rebind.CacheUtil;
import org.mvel2.util.NullType;

import com.google.gwt.core.ext.GeneratorContext;

/**
 * @author Mike Brock
 */
public final class ClassScanner {
  public static class CacheHolder implements CacheStore {
    final Map<MetaClass, Collection<MetaClass>> subtypesCache = new ConcurrentHashMap<MetaClass, Collection<MetaClass>>();

    @Override
    public void clear() {
      subtypesCache.clear();
    }
  }

  private static final ThreadLocal<Boolean> reflectionScanLocal = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };

  private static AtomicLong totalClassScanTime = new AtomicLong(0);

  private ClassScanner() {
  }

  public static Collection<MetaParameter> getParametersAnnotatedWith(final Class<? extends Annotation> annotation,
          final Set<String> packages, final GeneratorContext genCtx) {

    final Collection<MetaParameter> result = new HashSet<MetaParameter>();

    if (genCtx != null) {
      for (final MetaClass metaClass : getAllReloadableCachedClasses(genCtx)) {
        for (final MetaMethod method : metaClass.getDeclaredMethods()) {
          for (final MetaParameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(annotation)) {
              result.add(parameter);
            }
          }
        }
      }
      try {
        for (final Method m : ScannerSingleton.getOrCreateInstance().getMethodsWithAnyParamAnnotated(annotation)) {
          Class<?> clazz = m.getDeclaringClass();
          if (!isReloadable(clazz) && MetaClassFactory.isKnownType(clazz.getName())) {
            MetaMethod mm = new JavaReflectionMethod(m);
            for (final MetaParameter parameter : mm.getParameters()) {
              if (parameter.isAnnotationPresent(annotation)) {
                result.add(parameter);
              }
            }
          }
        }
      } catch (Exception ignored) {
      }
    }
    else {
      for (final MetaClass metaClass : MetaClassFactory.getAllCachedClasses()) {
        for (final MetaMethod method : metaClass.getDeclaredMethods()) {
          for (final MetaParameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(annotation)) {
              result.add(parameter);
            }
          }
        }
      }
    }

    filterResultsParameter(result, packages, null);
    return Collections.unmodifiableCollection(result);
  }

  public static Collection<MetaParameter> getParametersAnnotatedWith(final Class<? extends Annotation> annotation,
          final GeneratorContext genCtx) {
    return getParametersAnnotatedWith(annotation, null, genCtx);
  }

  public static Collection<MetaClass> getTypesAnnotatedWith(final Class<? extends Annotation> annotation,
          final Set<String> packages, final String excludeRegEx, final GeneratorContext genCtx, boolean reflections) {

    final Collection<MetaClass> result = new HashSet<MetaClass>();

    if (genCtx != null) {
      for (final MetaClass metaClass : getAllReloadableCachedClasses(genCtx)) {
        if (metaClass.isAnnotationPresent(annotation)) {
          result.add(metaClass);
        }
      }
      try {
        for (final Class<?> cls : ScannerSingleton.getOrCreateInstance().getTypesAnnotatedWith(annotation)) {
          if (!isReloadable(cls) && MetaClassFactory.isKnownType(cls.getName())) {
            result.add(MetaClassFactory.get(cls));
          }
        }
      } catch (Exception ignored) {
      }
    }
    else {
      for (final MetaClass metaClass : MetaClassFactory.getAllCachedClasses()) {
        if (metaClass.isAnnotationPresent(annotation)) {
          result.add(metaClass);
        }
      }

      try {
        if (reflections || reflectionScanLocal.get()) {
          for (final Class<?> cls : ScannerSingleton.getOrCreateInstance().getTypesAnnotatedWith(annotation)) {
            final MetaClass e = MetaClassFactory.get(cls);
            result.add(e);
          }
        }
      } catch (Exception ignored) {
      }
    }
    filterResultsClass(result, packages, excludeRegEx);
    return Collections.unmodifiableCollection(result);
  }

  public static Collection<MetaClass> getTypesAnnotatedWith(final Class<? extends Annotation> annotation) {
    return getTypesAnnotatedWith(annotation, null, null, null, false);
  }

  public static Collection<MetaClass> getTypesAnnotatedWith(final Class<? extends Annotation> annotation,
          boolean reflections) {
    return getTypesAnnotatedWith(annotation, null, null, null, reflections);
  }

  public static Collection<MetaClass> getTypesAnnotatedWith(final Class<? extends Annotation> annotation,
          final GeneratorContext genCtx) {
    return getTypesAnnotatedWith(annotation, null, null, genCtx, true);
  }

  public static Collection<MetaClass> getTypesAnnotatedWith(final Class<? extends Annotation> annotation,
          final Set<String> packages, final GeneratorContext genCtx) {
    return getTypesAnnotatedWith(annotation, packages, null, genCtx, true);
  }

  public static Collection<MetaClass> getTypesAnnotatedWithExcluding(final Class<? extends Annotation> annotation,
          final String excludeRegex, final GeneratorContext genCtx) {
    return getTypesAnnotatedWith(annotation, null, excludeRegex, genCtx, true);
  }

  public static Collection<MetaMethod> getMethodsAnnotatedWith(final Class<? extends Annotation> annotation,
          final Set<String> packages, final GeneratorContext genCtx) {
    final Collection<MetaMethod> result = new HashSet<MetaMethod>(50);

    if (genCtx != null) {
      for (final MetaClass metaClass : getAllReloadableCachedClasses(genCtx)) {
        for (final MetaMethod metaMethod : metaClass.getDeclaredMethods()) {
          if (metaMethod.isAnnotationPresent(annotation)) {
            result.add(metaMethod);
          }
        }
      }
      try {
        for (final Method m : ScannerSingleton.getOrCreateInstance().getMethodsAnnotatedWith(annotation)) {
          Class<?> clazz = m.getDeclaringClass();
          if (!isReloadable(clazz) && MetaClassFactory.isKnownType(clazz.getName())) {
            final MetaClass metaClass = MetaClassFactory.get(clazz);
            result.add(new JavaReflectionMethod(metaClass, m));
          }
        }
      } catch (Exception ignored) {
      }
    }
    else {
      for (final MetaClass metaClass : MetaClassFactory.getAllCachedClasses()) {
        for (final MetaMethod metaMethod : metaClass.getDeclaredMethods()) {
          if (metaMethod.isAnnotationPresent(annotation)) {
            result.add(metaMethod);
          }
        }
      }
    }

    filterResultsMethod(result, packages, null);
    return Collections.unmodifiableCollection(result);
  }

  public static Collection<MetaField> getFieldsAnnotatedWith(final Class<? extends Annotation> annotation,
          final Set<String> packages, final GeneratorContext genCtx) {
    final Collection<MetaField> result = new HashSet<MetaField>(50);

    if (genCtx != null) {
      for (final MetaClass metaClass : getAllReloadableCachedClasses(genCtx)) {
        for (final MetaField metaField : metaClass.getDeclaredFields()) {
          if (metaField.isAnnotationPresent(annotation)) {
            result.add(metaField);
          }
        }
      }
      try {
        for (final Field f : ScannerSingleton.getOrCreateInstance().getFieldsAnnotatedWith(annotation)) {
          Class<?> clazz = f.getDeclaringClass();
          if (!isReloadable(clazz) && MetaClassFactory.isKnownType(clazz.getName())) {
            result.add(new JavaReflectionField(f));
          }
        }
      } catch (Exception ignored) {
      }
    }
    else {
      for (final MetaClass metaClass : MetaClassFactory.getAllCachedClasses()) {
        for (final MetaField metaField : metaClass.getDeclaredFields()) {
          if (metaField.isAnnotationPresent(annotation)) {
            result.add(metaField);
          }
        }
      }
    }

    filterResultsField(result, packages, null);
    return Collections.unmodifiableCollection(result);
  }

  public static Collection<MetaClass> getSubTypesOf(final MetaClass metaClass, final GeneratorContext genCtx) {
    final MetaClass root = metaClass.getErased();

    final CacheHolder cache = CacheUtil.getCache(CacheHolder.class);

    if (cache.subtypesCache.containsKey(root)) {
      return cache.subtypesCache.get(root);
    }

    final Set<MetaClass> result = Collections.newSetFromMap(new ConcurrentHashMap<MetaClass, Boolean>());

    if (!Boolean.getBoolean("org.jboss.errai.skip.reloadable.subtypes")) {
      for (final MetaClass mc : getAllReloadableCachedClasses(genCtx)) {
        if (!NullType.class.getName().equals(mc.getFullyQualifiedName())
                && !root.getFullyQualifiedName().equals(mc.getFullyQualifiedName()) && root.isAssignableFrom(mc)) {
          result.add(mc.getErased());
        }
      }
    }

    final Class<?> cls = root.asClass();
    if (cls != null && !cls.equals(NullType.class)) {
      for (final Class<?> c : ScannerSingleton.getOrCreateInstance().getSubTypesOf(cls)) {
        if (!c.isAnonymousClass() && !c.isSynthetic()) {
          result.add(MetaClassFactory.get(c));
        }
      }
    }

    cache.subtypesCache.put(root, result);
    return result;
  }

  private static Set<String> reloadablePackages = null;
  private static Set<String> allReloadablePackages = new HashSet<String>();
  private static ConcurrentHashMap<GeneratorContext,ArrayList<MetaClass>> cachedClasses = new ConcurrentHashMap<GeneratorContext,ArrayList<MetaClass>>();
  
  private static Collection<MetaClass> getAllReloadableCachedClasses(final GeneratorContext context) {

		
		if (reloadablePackages == null) {
		      reloadablePackages = RebindUtils.getReloadablePackageNames(context);
		}
			
			if( cachedClasses.containsKey( context ) )
				return cachedClasses.get( context );
		    
//			System.out.println("GettingAllReloadableCachedClasses");  
			
			ArrayList<MetaClass> clazzes = new ArrayList<MetaClass>();
		    for (MetaClass clazz : MetaClassFactory.getAllCachedClasses()) {
		  	  String packageName = clazz.getPackageName();
		  	  
		  	  // see if we have encountered this EXACT package before
		  	  if( allReloadablePackages.contains(packageName) )
		  		  clazzes.add(clazz);
		  	  else {
		    	for (String reloadablePackage : reloadablePackages) {
			        if (clazz.getFullyQualifiedName().startsWith(reloadablePackage)) {
			        	allReloadablePackages.add(clazz.getPackageName());
			        	clazzes.add(clazz);
			        }
		    	}
		      }
		    }
		    
//		    cachedClasses.put(context, clazzes);
		    return clazzes;	
  }

  private static boolean isReloadable(Class<?> clazz) {

  	  if( allReloadablePackages.contains(clazz.getPackage().getName()) )
  		  return true;
  	  
  	  for (String reloadablePackage : reloadablePackages) {
      if (clazz.getName().startsWith(reloadablePackage)) {
        return true;
      }
    }
    return false;
  }

  private static void filterResultsClass(final Collection<MetaClass> result, final Set<String> packages,
          final String excludeRegEx) {

    final Pattern excludePattern;
    if (excludeRegEx != null) {
      excludePattern = Pattern.compile(excludeRegEx);
    }
    else {
      excludePattern = null;
    }

    final Iterator<MetaClass> filterIterator = result.iterator();
    while (filterIterator.hasNext()) {
      _removeIfNotMatches(filterIterator, filterIterator.next(), packages, excludePattern);
    }
  }

  private static void filterResultsMethod(final Collection<MetaMethod> result, final Set<String> packages,
          final String excludeRegEx) {

    final Pattern excludePattern;
    if (excludeRegEx != null) {
      excludePattern = Pattern.compile(excludeRegEx);
    }
    else {
      excludePattern = null;
    }

    final Iterator<MetaMethod> filterIterator = result.iterator();
    while (filterIterator.hasNext()) {
      _removeIfNotMatches(filterIterator, filterIterator.next().getDeclaringClass(), packages, excludePattern);
    }
  }

  private static void filterResultsField(final Collection<MetaField> result, final Set<String> packages,
          final String excludeRegEx) {

    final Pattern excludePattern;
    if (excludeRegEx != null) {
      excludePattern = Pattern.compile(excludeRegEx);
    }
    else {
      excludePattern = null;
    }

    final Iterator<MetaField> filterIterator = result.iterator();
    while (filterIterator.hasNext()) {
      _removeIfNotMatches(filterIterator, filterIterator.next().getDeclaringClass(), packages, excludePattern);
    }
  }

  private static void filterResultsParameter(final Collection<MetaParameter> result, final Set<String> packages,
          final String excludeRegEx) {

    final Pattern excludePattern;
    if (excludeRegEx != null) {
      excludePattern = Pattern.compile(excludeRegEx);
    }
    else {
      excludePattern = null;
    }

    final Iterator<MetaParameter> filterIterator = result.iterator();
    while (filterIterator.hasNext()) {
      _removeIfNotMatches(filterIterator, filterIterator.next().getDeclaringMember().getDeclaringClass(), packages,
              excludePattern);
    }
  }

  private static void _removeIfNotMatches(final Iterator<?> iterator, final MetaClass type, final Set<String> packages,
          final Pattern excludePattern) {

    if (packages == null || packages.contains(type.getPackageName())) {
      if (excludePattern != null && excludePattern.matcher(type.getFullyQualifiedName()).matches()) {
        iterator.remove();
      }
    }
    else {
      iterator.remove();
    }
  }

  public static void setReflectionsScanning(final boolean bool) {
    reflectionScanLocal.set(bool);
  }

  public static AtomicLong getTotalClassScanTime() {
    return totalClassScanTime;
  }
}
