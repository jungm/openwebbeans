/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.webbeans.ejb.util;

import java.lang.reflect.Method;

import org.apache.webbeans.ejb.EJBUtil;
import org.apache.webbeans.ejb.EjbType;
import org.apache.webbeans.ejb.component.EjbComponentImpl;
import org.apache.webbeans.util.Asserts;
import org.apache.webbeans.util.ClassUtil;

public final class EjbUtility
{
    private EjbUtility()
    {
        
    }
    
    
    public static EjbType getEjbTypeForAnnotatedClass(Class<?> ejbClass)
    {
        Asserts.assertNotNull(ejbClass, "ejbClass parameter can not be null");
        
        if(EJBUtil.isEJBSessionStateless(ejbClass))
        {
            return EjbType.STATELESS;
        }
        else if(EJBUtil.isEJBSessionStatefulClass(ejbClass))
        {
            return EjbType.STATEFULL;
        }
        //TODO ejb 3.1 jars
        else
        {
            return EjbType.SINGLETON;
        }
    }
    
    public static void configureEjbBusinessMethods(EjbComponentImpl<?> ejbComponent, Class<?> businessInterface)
    {
        Asserts.assertNotNull(ejbComponent,"ejbComponent parameter can not be null");
        Asserts.assertNotNull(businessInterface, "businessInterface parameter can not be null");
        
        if(!businessInterface.equals(Object.class))
        {
            Method[] methods = businessInterface.getMethods();
            
            for(Method method : methods)
            {
                if(!ClassUtil.isObjectMethod(method.getName()))
                {
                    ejbComponent.addBusinessMethod(method);   
                }
            }                
        }

    }
    

}
