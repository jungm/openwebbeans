/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.apache.webbeans.container;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.New;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;

import org.apache.webbeans.annotation.AnyLiteral;
import org.apache.webbeans.annotation.DefaultLiteral;
import org.apache.webbeans.component.AbstractBean;
import org.apache.webbeans.config.WebBeansFinder;
import org.apache.webbeans.exception.WebBeansConfigurationException;
import org.apache.webbeans.exception.inject.NullableDependencyException;
import org.apache.webbeans.util.AnnotationUtil;
import org.apache.webbeans.util.Asserts;
import org.apache.webbeans.util.ClassUtil;
import org.apache.webbeans.util.WebBeansUtil;

/**
 * Injection point resolver class. 
 * 
 * <p>
 * It is a singleton class per ClassLoader per JVM. It is
 * responsible for resolbing the bean instances at the injection points for 
 * its bean manager.
 * </p>
 * 
 * @version $Rev$ $Date$
 * @see WebBeansFinder
 */
public class InjectionResolver
{
    /**Bean Manager*/
    private BeanManagerImpl manager;
    
    /**
     * Creates a new injection resolve for given bean manager.
     * 
     * @param manager bean manager
     */
    public InjectionResolver(BeanManagerImpl manager)
    {
        this.manager = manager;

    }

    /**
     * Returns bean manager injection resolver.
     * 
     * @return bean manager injection resolver
     * @see WebBeansFinder
     */
    public static InjectionResolver getInstance()
    {
        InjectionResolver instance = BeanManagerImpl.getManager().getInjectionResolver();
        
        return instance;
    }
    
    /**
     * Check the type of the injection point.
     * <p>
     * Injection point type can not be {@link TypeVariable}.
     * </p>
     * 
     * @param injectionPoint injection point
     * @throws WebBeansConfigurationException if not obey the rule
     */
    public void checkInjectionPointType(InjectionPoint injectionPoint)
    {
        Type type = injectionPoint.getType();
        
        //Check for injection point type variable
        if(ClassUtil.isTypeVariable(type))
        {
            throw new WebBeansConfigurationException("Injection point type : " + injectionPoint +  " can not define Type Variable generic type");
        }
        
    }

    /**
     * Check that bean exist in the deployment for given
     * injection point definition.
     * 
     * @param injectionPoint injection point
     * @throws If bean is not avialable in the current deployment for given injection
     */
    public void checkInjectionPoints(InjectionPoint injectionPoint)
    {        
        WebBeansUtil.checkInjectionPointNamedQualifier(injectionPoint);
        
        Type type = injectionPoint.getType();        
        Class<?> clazz = null;
        
        if(ClassUtil.isTypeVariable(type))
        {
           throw new WebBeansConfigurationException("Injection point type : " + injectionPoint + " type can not be defined as Typevariable or Wildcard type!");
        }
        
        if (type instanceof ParameterizedType)
        {
            ParameterizedType pt = (ParameterizedType) type;
            
            clazz = (Class<?>) pt.getRawType();
        }
        else
        {
            clazz = (Class<?>) type;
        }
        
        Annotation[] qualifiers = new Annotation[injectionPoint.getQualifiers().size()];
        qualifiers = injectionPoint.getQualifiers().toArray(qualifiers);
        
        Set<Bean<?>> beanSet = implResolveByType(type, qualifiers);
        
        if(beanSet.isEmpty())
        {
            if(qualifiers.length == 1 && qualifiers[0].annotationType().equals(New.class))
            {
                New newQualifier =  (New)qualifiers[0];
                
                if(newQualifier.value() == New.class)
                {
                    beanSet.add(WebBeansUtil.createNewComponent(clazz));    
                }
                else
                {
                    beanSet.add(WebBeansUtil.createNewComponent(newQualifier.value()));
                }
                
            }
        }
        
        ResolutionUtil.checkResolvedBeans(beanSet, clazz, qualifiers, injectionPoint);
        
        Bean<?> bean = beanSet.iterator().next();
        
        if(clazz.isPrimitive())
        {
            if(bean.isNullable())
            {
                throw new NullableDependencyException("Injection point type : " + injectionPoint + " type is primitive but resolved bean can have nullable objects!");
            }
        }
        
    }
    
        
    /**
     * Returns bean for injection point.
     * 
     * @param injectionPoint injection point declaration
     * @return bean for injection point
     */
    public Bean<?> getInjectionPointBean(InjectionPoint injectionPoint)
    {

        Type type = injectionPoint.getType();        
        Class<?> clazz = null;
        
        if (type instanceof ParameterizedType)
        {
            ParameterizedType pt = (ParameterizedType) type;            
            clazz = (Class<?>) pt.getRawType();
                        
            
        }
        else
        {
            clazz = (Class<?>) type;
        }
        
        Annotation[] qualifiers = injectionPoint.getQualifiers().toArray(new Annotation[0]);        
        if(isInstanceOrEventInjection(type))
        {
            qualifiers = new Annotation[1];
            qualifiers[0] = new AnyLiteral();
        }
        
        Set<Bean<?>> beanSet = implResolveByType(type, qualifiers);
        
        if(beanSet.isEmpty())
        {
            if(qualifiers.length == 1 && qualifiers[0].annotationType().equals(New.class))
            {
                New newQualifier =  (New)qualifiers[0];
                
                if(newQualifier.value() == New.class)
                {
                    beanSet.add(WebBeansUtil.createNewComponent(clazz));    
                }
                else
                {
                    beanSet.add(WebBeansUtil.createNewComponent(newQualifier.value()));
                }

            }
        }
        

        ResolutionUtil.checkResolvedBeans(beanSet, clazz, qualifiers, injectionPoint);   

        return beanSet.iterator().next();
        
    }    
    
    
    private boolean isInstanceOrEventInjection(Type type)
    {
        Class<?> clazz = null;
        boolean injectInstanceOrEventProvider = false;
        if (type instanceof ParameterizedType)
        {
            ParameterizedType pt = (ParameterizedType) type;            
            clazz = (Class<?>) pt.getRawType();
                        
            if(clazz.isAssignableFrom(Instance.class) || clazz.isAssignableFrom(Event.class))
            {
                injectInstanceOrEventProvider = true;
            }            
        }
        
        return injectInstanceOrEventProvider;
    }
    
        
    /**
     * Returns set of beans for given bean name.
     * 
     * @param name bean name
     * @return set of beans for given bean name
     */
    public Set<Bean<?>> implResolveByName(String name)
    {
        Asserts.assertNotNull(name, "name parameter can not be null");

        Set<Bean<?>> resolvedComponents = new HashSet<Bean<?>>();        
        Set<Bean<?>> deployedComponents = this.manager.getBeans();
        
        Iterator<Bean<?>> it = deployedComponents.iterator();
        //Finding all beans with given name
        while (it.hasNext())
        {
            Bean<?> component = it.next();
            if (component.getName() != null)
            {
                if (component.getName().equals(name))
                {
                     resolvedComponents.add(component);
                }
            }
        }
        
        //Look for enable/disable
        resolvedComponents = findByEnabled(resolvedComponents);

        //Still Ambigious, check for specialization
        if(resolvedComponents.size() > 1)
        {
            //Check for specialization
            Set<Bean<?>> specializedComponents = findSpecializedForNameResolution(resolvedComponents);        
            if(specializedComponents.size() > 0)
            {
                return specializedComponents;
            }            
        }
                
        return resolvedComponents;
    }
     
    private Set<Bean<?>> findByEnabled(Set<Bean<?>> resolvedComponents)
    {
        Set<Bean<?>> specializedComponents = new HashSet<Bean<?>>(); 
        if(resolvedComponents.size() > 0)
        {
            for(Bean<?> bean : resolvedComponents)
            {
                AbstractBean<?> component = (AbstractBean<?>)bean;
                
                if(component.isEnabled())
                {
                    specializedComponents.add(component);
                }
            }
        }
        
        return specializedComponents;
        
    }
    
    
    /**
     * Returns filtered set by specialization.
     * 
     * @param resolvedComponents result beans
     * @return filtered set by specialization
     */
    private Set<Bean<?>> findSpecializedForNameResolution(Set<Bean<?>> resolvedComponents)
    {
        Set<Bean<?>> specializedComponents = new HashSet<Bean<?>>(); 
        if(resolvedComponents.size() > 0)
        {
            for(Bean<?> bean : resolvedComponents)
            {
                AbstractBean<?> component = (AbstractBean<?>)bean;
                
                if(component.isSpecializedBean())
                {
                    specializedComponents.add(component);
                }
            }
        }
        
        return specializedComponents;
    }
    
    /**
     * Resolution by type.
     * 
     * @param <T> bean type info
     * @param injectionPointType injection point api type
     * @param injectionPointTypeArguments actual type arguments if parameterized type
     * @param qualifier qualifier of the injection point
     * @return set of resolved beans
     */
    public Set<Bean<?>> implResolveByType(Type injectionPointType, Annotation... qualifier)
    {
        Asserts.assertNotNull(injectionPointType, "injectionPointType parameter can not be null");
        Asserts.assertNotNull(qualifier, "qualifier parameter can not be null");
        
        Set<Bean<?>> results = new HashSet<Bean<?>>();
        Set<Bean<?>> deployedComponents = this.manager.getBeans();

        boolean currentQualifier = false;
        boolean returnAll = false;
        
        if(isInstanceOrEventInjection(injectionPointType))
        {
            qualifier = new Annotation[1];
            qualifier[0] = new AnyLiteral();
        }
        
        else
        {
            if (qualifier.length == 0)
            {
                qualifier = new Annotation[1];
                qualifier[0] = new DefaultLiteral();
                currentQualifier = true;
            }                        
        }
        
        if (injectionPointType.equals(Object.class) && currentQualifier)
        {
            returnAll = true;
        }

        Iterator<Bean<?>> it = deployedComponents.iterator();

        while (it.hasNext())
        {
            Bean<?> component = it.next();

            if (returnAll)
            {
                results.add((Bean<?>) component);
                continue;
            }

            else
            {
                Set<Type> componentApiTypes = component.getTypes();
                Iterator<Type> itComponentApiTypes = componentApiTypes.iterator();
                while (itComponentApiTypes.hasNext())
                {
                    Type componentApiType = itComponentApiTypes.next();                    
                    
                    if(ClassUtil.isAssignable(componentApiType, injectionPointType))
                    {
                        results.add((Bean<?>) component);
                        break;                                            
                    }                    
                }
            }            
        }
 
        //Look for qualifiers
        results = findByQualifier(results, qualifier);
        
        //Look for alternative
        results = findByAlternatives(results);

        //Ambigious resulotion, check for specialization
        if(results.size() > 1)
        {
            //Look for specialization
            results = findBySpecialization(results);            
        }
        
        return results;
    }
    
    /**
     * Returns specialized beans if exists, otherwise return input result
     * 
     * @param <T> bean class type
     * @param result result beans
     * @return specialized beans if exists, otherwise return input result
     */
    public Set<Bean<?>> findBySpecialization(Set<Bean<?>> result)
    {
        Iterator<Bean<?>> it = result.iterator();
        Set<Bean<?>> res = new HashSet<Bean<?>>();
        
        while(it.hasNext())
        {
            AbstractBean<?> component = (AbstractBean<?>)it.next();
            if(component.isSpecializedBean())
            {
                res.add(component);
            }
        }
        
        if(res.size() > 0)
        {
            return res;
        }
        
        return result;
    }
        
    /**
     * Gets alternatives from set.
     * @param result resolved set
     * @return containes alternatives
     */
    public Set<Bean<?>> findByAlternatives(Set<Bean<?>> result)
    {
        Set<Bean<?>> alternativeSet = new HashSet<Bean<?>>();
        Set<Bean<?>> enableSet = new HashSet<Bean<?>>();
        boolean containsAlternative = false;
        
        for(Bean<?> bean : result)
        {
            if(bean.isAlternative())
            {
                if(!containsAlternative)
                {
                    containsAlternative = true;
                }
                alternativeSet.add(bean);
            }
            else
            {
                if(!containsAlternative)
                {                    
                    AbstractBean<?> temp = (AbstractBean<?>)bean;
                    if(temp.isEnabled())
                    {
                        enableSet.add(bean);
                    }                    
                }                
            }
        }
        
        if(containsAlternative)
        {
            return alternativeSet;
        }
        
        return enableSet;
    }

    /**
     * Returns filtered bean set according to the qualifiers.
     * 
     * @param <T> bean class
     * @param remainingSet bean set for filtering by qualifier
     * @param annotations qualifiers on injection point
     * @return filtered bean set according to the qualifiers
     */
    private Set<Bean<?>> findByQualifier(Set<Bean<?>> remainingSet, Annotation... annotations)
    {
        Iterator<Bean<?>> it = remainingSet.iterator();
        Set<Bean<?>> result = new HashSet<Bean<?>>();

        while (it.hasNext())
        {
            Bean<?> component = it.next();
            Set<Annotation> qTypes = component.getQualifiers();

            int i = 0;
            for (Annotation annot : annotations)
            {
                Iterator<Annotation> itQualifiers = qTypes.iterator();
                while (itQualifiers.hasNext())
                {
                    Annotation qualifier = itQualifiers.next();
                    if (annot.annotationType().equals(qualifier.annotationType()))
                    {
                        if (AnnotationUtil.hasAnnotationMember(qualifier.annotationType(), qualifier, annot))
                        {
                            i++;
                        }
                    }

                }
            }

            if (i == annotations.length)
            {
                result.add(component);
                i = 0;
            }

        }

        return result;
    }
}