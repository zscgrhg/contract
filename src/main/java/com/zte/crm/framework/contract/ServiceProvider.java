package com.zte.crm.framework.contract;

public interface ServiceProvider<T> {
   default T getProvider(){
       return null;
   }
}