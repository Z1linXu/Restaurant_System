package com.restaurant.system.allo;
/** Entities expose public snapshot fields. Bean property copying would silently lose them. */
public final class AlloCopies {
 private AlloCopies(){}
 public static <T>T copy(T source,T target) {
  if(source.getClass()!=target.getClass())throw new IllegalArgumentException("Snapshot type mismatch");
  try {for(var f:target.getClass().getFields())if(!java.lang.reflect.Modifier.isStatic(f.getModifiers()))f.set(target,f.get(source));return target;}
  catch(ReflectiveOperationException e){throw new IllegalStateException("Snapshot copy failed",e);}
 }
}
