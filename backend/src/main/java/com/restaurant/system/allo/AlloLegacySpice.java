package com.restaurant.system.allo;
import java.util.function.Supplier;
/** Only a frozen stale outbox or a persisted draft may retain the retired spice snapshot. */
public final class AlloLegacySpice {
 private static final ThreadLocal<Boolean> FROZEN=new ThreadLocal<>();
 private AlloLegacySpice(){}
 public static boolean frozen(){return Boolean.TRUE.equals(FROZEN.get());}
 public static <T>T withFrozen(boolean enabled,Supplier<T> action){
  Boolean previous=FROZEN.get();if(enabled)FROZEN.set(true);
  try{return action.get();}finally{if(previous==null)FROZEN.remove();else FROZEN.set(previous);}
 }
}
