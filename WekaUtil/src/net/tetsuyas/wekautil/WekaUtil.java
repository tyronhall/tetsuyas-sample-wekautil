package net.tetsuyas.wekautil;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.clusterers.Clusterer;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

import net.tetsuyas.wekautil.annotations.ClusterResultAttribute;
import net.tetsuyas.wekautil.annotations.NotAttrubute;
import net.tetsuyas.wekautil.annotations.ClassifiedClassAttribute;
import net.tetsuyas.wekautil.annotations.UsingAttribute;
import net.tetsuyas.wekautil.annotations.UsingOnlyHasAttributeAnnotationProperty;
import net.tetsuyas.wekautil.annotations.Weight;

public abstract class WekaUtil<T> {
//	private static final String ClassifiedClassAttribute = "classifiedClassAttribute";
	public static List<Class> numericClasses = Arrays.asList(
			new Class[]{int.class,double.class,Integer.class,Double.class});
	private static Logger logger = Logger.getLogger(WekaUtil.class);

	Class<?> clazz ;
	public final List<? extends T> list;
	boolean usingAttributeAnnotationProperty = false;

	List<PropertyDescriptor> attributePropertyDescriptors = new ArrayList<PropertyDescriptor>();
	FastVector attributeFastVector;
	Map<PropertyDescriptor, Attribute> attributeMap = new HashMap<PropertyDescriptor, Attribute>();
	Instances instances;
	private Annotation[] annotations;


	static{
		logger.setLevel(Level.DEBUG);
	}

	public WekaUtil(List<? extends T> list){
		if(list.isEmpty()) throw new IllegalStateException("list is empty");
		this.list = list;
		this.clazz = list.get(0).getClass();

		usingAttributeAnnotationProperty =
			hasAnnotation(UsingOnlyHasAttributeAnnotationProperty.class, clazz.getAnnotations());

		registPropertyDescriptors();
		getAttributeFastVector();

	}
	void registPropertyDescriptors() {
		BeanInfo beanInfo = null;
		try{
			beanInfo = Introspector.getBeanInfo(clazz);
			PropertyDescriptor[] pdArray = beanInfo.getPropertyDescriptors();
			logger.debug(pdArray.length+"#Pdarray Length");
			for(PropertyDescriptor pd:pdArray){
				if(pd.getName().equals("class")) {
					continue;
				}
				annotations = pd.getReadMethod().getAnnotations();

//				サブクラスごとに特殊なAttributeを登録する
				if(registSubClassPropertyDescriptor(pd)){
					continue ;
				}
				if(usingAttributeAnnotationProperty){
					/**Attributeアノテーションを持っている要素のみを対象にする。*/
					if( ! hasAnnotation(UsingAttribute.class,pd)){
						continue;
					}
				}else{
					/**NotAttributeアノテーションを持っている要素のみを対象にする。*/
					if(hasAnnotation(NotAttrubute.class,pd)){
						continue;
					}
				}
				/*数値または文字列の場合に登録する。*/
				if(isNumericValue(pd)
					||pd.getReadMethod().getReturnType().equals(String.class)){
					attributePropertyDescriptors.add(pd);
					continue;
				}			}
		} catch (IntrospectionException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**サブクラスでオーバーライドする。*/
	abstract boolean registSubClassPropertyDescriptor(PropertyDescriptor pd);
	abstract void registSubClassAttributeFastVector();
	abstract public Instances getInstances() ;

	/**サブクラスごとに必要な処理をregistSubClassAttributeFastVector()で追加する。*/
	FastVector getAttributeFastVector(){
		if(attributeFastVector!=null) return attributeFastVector;
		attributeFastVector = new FastVector(attributePropertyDescriptors.size());
		for(PropertyDescriptor attributePd:attributePropertyDescriptors){
			if(!attributePd.getReadMethod().getReturnType().equals(String.class)
					&& !isNumericValue(attributePd)){
					continue;
			}
			Attribute attribute;
			if(isNumericValue(attributePd)){
				attribute = new Attribute(attributePd.getName());
			}else{
//				返り値がStringのオブジェクトについては内容の集合を設定値として与える。
				attribute = createAttributeNominalValues(attributePd);
			}
//			Weightをセットする。
			for(Annotation annotation :attributePd.getReadMethod().getAnnotations()){
				if (annotation instanceof Weight) {
					Weight weight = (Weight) annotation;
					double value = Double.parseDouble(weight.value());
					attribute.setWeight(value);
				}
			}
			attributeFastVector.addElement(attribute);
			attributeMap.put(attributePd, attribute);
		}
		registSubClassAttributeFastVector();
		return attributeFastVector;
	}


	/**
	 * 指定されたnominalな属性のAttributeを生成する<br/>
	 * 学習のために与えられたリストのすべての当該属性の内容のSetを生成して、Attributeとして返す。
	 * @param pd
	 * @return
	 */
	Attribute createAttributeNominalValues(PropertyDescriptor pd){
		Set<String> StringValueSet = new HashSet<String>();
		for(Object obj :list){
			try {
//			logger.debug(pd.getReadMethod().getName());
				Object value = pd.getReadMethod().invoke(obj, null);
				StringValueSet.add(value.toString());
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}

		FastVector fv = new FastVector(StringValueSet.size());
		for(String value:StringValueSet){
			fv.addElement(value);
		}
		Attribute attribute = new Attribute(pd.getName(), fv);

		Enumeration<?> enum1 = attribute.enumerateValues();
		while(enum1.hasMoreElements()){
			logger.debug(enum1.nextElement().toString());
		}
		return attribute;
	}


	Instance createInstance(T obj, int attributeSize){
		Instance instance = new Instance(attributeSize);
		for(PropertyDescriptor pd:attributePropertyDescriptors){
			try {
				Object value = pd.getReadMethod().invoke(obj, null);
				if(value!=null){
					Attribute attribute = attributeMap.get(pd);
					if(attribute==null){
//						logger.debug(pd.getName()+"に対応するAttributeは存在しません。");
						continue;
					}
					if(attribute.isNumeric()){
						instance.setValue(attribute, Double.valueOf(value.toString()));
					}
					if(attribute.isNominal()){
						instance.setValue(attribute, value.toString());
					}
				}
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		return instance;
	}



	boolean hasAnnotation(Class<? extends Annotation> checkAnnotation,PropertyDescriptor pd){
		Field field  = getDecrearedField(pd);
		if(field!=null && hasAnnotation(checkAnnotation, field.getAnnotations())){
				return true;
		}

		Method readMethod = pd.getReadMethod();
		if(readMethod!=null && hasAnnotation(checkAnnotation, readMethod.getAnnotations())){
			return true;
		}

		Method writeMethod = pd.getWriteMethod();
		if(writeMethod!=null && hasAnnotation(checkAnnotation, writeMethod.getAnnotations())){
			return true;
		}

		return false;
	}

	private boolean hasAnnotation(Class<? extends Annotation> checkAnnotation,Annotation[] annotations){
		for(Annotation anno :annotations){
			if(anno.annotationType().equals(checkAnnotation)) return true;
		}
		return false;
	}

	private Field getDecrearedField(PropertyDescriptor pd){
		for(Field field:getAllDeclaredInstanceFields()){
			if(field.getName().equals(pd.getName())
					&& pd.getPropertyType().equals(field.getType())) return field;
		}
		return null;
	}

	private Set<Field> getAllDeclaredInstanceFields(){
		Set<Field> fields = new HashSet<Field>();
		Class<?> tempClass =clazz;
		do{
			fields.addAll(Arrays.asList(tempClass.getDeclaredFields()));
		}while(
			(tempClass = tempClass.getSuperclass())!=null
				&&	!tempClass.isInterface()
				&&	!tempClass.equals(Object.class)
			);
		Set<Field> res = new HashSet<Field>();
// staticなフィールドを排除する。
		for(Field f:fields){
			if(!Modifier.isStatic(f.getModifiers())){
				res.add(f);
			}
		}
		return res;
	}

	boolean isNumericValue(PropertyDescriptor pd){
		return numericClasses.contains(pd.getReadMethod().getReturnType());
	}

	boolean hasAttributeValue(Attribute attribute,Object object){
		if(!attribute.isNominal()) return false;
		Enumeration<?> enumeration = attribute.enumerateValues();
		while(enumeration.hasMoreElements()){
			if(enumeration.nextElement().equals(object)){
				return true;

			}
		}
		return false;
	}


}

