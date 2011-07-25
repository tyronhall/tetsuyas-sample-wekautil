package net.tetsuyas.wekautil;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.log4j.Logger;

import net.tetsuyas.wekautil.annotations.ClassifiedClassAttribute;
import net.tetsuyas.wekautil.annotations.ClusterResultAttribute;
import net.tetsuyas.wekautil.annotations.TrainingClassAttribute;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

public class ClassifierUtil<T> extends WekaUtil<T> {
	private static Logger logger = Logger.getLogger(ClassifierUtil.class);

	Classifier classifier;
	Evaluation evaluation;
	PropertyDescriptor traingingClassPropertyDescriptor;
	PropertyDescriptor classifiedClassPropertyDescriptor;
	Attribute trainingClassAttribute;
//	Attribute classifiedClassAttribute;
	private static final boolean isLearning = true;
	private static final boolean isNOTLearning = false;


	/**
	 * インスタンスのまま分類するメソッド<br/>
	 * 事前に分類器をセットしておく必要ある。
	 *
	 * @author tetsuyas
	 * @date 2011/07/22
	 * */
	public T classify(T obj){
		if(getClassifier()==null) throw new IllegalStateException();
		try {
			Instance classifyInstance = createClassifyInstance(obj);
			if(classifyInstance==null) logger.debug("classifyInstance is null");
			double result = getClassifier().classifyInstance(classifyInstance);

			Object resultValueObj  =null;
			if(trainingClassAttribute.isNominal()){
				resultValueObj = trainingClassAttribute.value((int)result);
			}else{
				if(classifiedClassPropertyDescriptor.getReadMethod().getReturnType().equals(double.class)
					|| classifiedClassPropertyDescriptor.getReadMethod().getReturnType().equals(Double.class)
				){
					resultValueObj = result;
				}else if(classifiedClassPropertyDescriptor.getReadMethod().getReturnType().equals(double.class)
						|| classifiedClassPropertyDescriptor.getReadMethod().getReturnType().equals(Double.class)
				){
					resultValueObj = (int)result;
				}
			}
			if(resultValueObj!=null){
				classifiedClassPropertyDescriptor.getWriteMethod().invoke(obj, resultValueObj);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return obj;

	}

	public ClassifierUtil(List<? extends T> list) {
		super(list);
		getInstances();

		try {
			evaluation = new Evaluation(getInstances());
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public Classifier getClassifier() {
		return classifier;
	}
	public void setClassifier(Classifier classifier) {
		this.classifier = classifier;
		try {
			classifier.buildClassifier(instances);
			evaluation .evaluateModel(classifier, getInstances());
			System.out.println(evaluation.toSummaryString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	boolean registSubClassPropertyDescriptor(PropertyDescriptor pd) {

		if(hasAnnotation(TrainingClassAttribute.class, pd)
				|| (trainingClassAttribute!=null && pd.getName().toLowerCase()
						.equals(TrainingClassAttribute.class.getSimpleName().toLowerCase()))){
				traingingClassPropertyDescriptor = pd;
				return true;
		}

		if(hasAnnotation(ClassifiedClassAttribute.class, pd)
				|| (classifiedClassPropertyDescriptor!=null && pd.getName().toLowerCase()
						.equals(ClassifiedClassAttribute.class.getSimpleName().toLowerCase()))){
				classifiedClassPropertyDescriptor = pd;
				return true;
		}
		if(hasAnnotation(ClusterResultAttribute.class, pd)){
			return true;
		}
		return false;
	}

	@Override
	void registSubClassAttributeFastVector() {

		if(traingingClassPropertyDescriptor ==null) logger.error("trainingClassPropertyDescriptor");
		if(isNumericValue(traingingClassPropertyDescriptor)){
			 trainingClassAttribute =new Attribute(traingingClassPropertyDescriptor.getName());
		}else{
			trainingClassAttribute = createAttributeNominalValues(traingingClassPropertyDescriptor);
		}
		attributeFastVector.addElement(trainingClassAttribute);
		attributeMap.put(traingingClassPropertyDescriptor, trainingClassAttribute);

//		if(classifiedClassPropertyDescriptor ==null) logger.error("classifiedClassPropertyDescriptor");
//		if(isNumericValue(classifiedClassPropertyDescriptor)){
//			 classifiedClassAttribute =new Attribute(classifiedClassPropertyDescriptor.getName());
//		}else{
//			classifiedClassAttribute = createAttributeNominalValues(classifiedClassPropertyDescriptor);
//		}
//		attributeFastVector.addElement(classifiedClassAttribute);
//		attributeMap.put(classifiedClassPropertyDescriptor, classifiedClassAttribute);
	}
	/**
	 * 学習用Instanceを生成する
	 * */
	public Instance createLearningInstance(T obj){
		Instance  instance = super.createInstance(obj, attributeFastVector.size());
		addClassAttributeValue(obj,instance);
		return instance;
	}

	/**
	 * 分類結果となる属性を設定する。
	 * */
	private void addClassAttributeValue(T obj,Instance instance){
		try {
			Object classValueObj = traingingClassPropertyDescriptor.getReadMethod().invoke(obj, null);
			Attribute classAttribute= attributeMap.get(traingingClassPropertyDescriptor);
			/** nominal で　値が含まれない場合は何も入れない。*/
			if(classAttribute.isNominal()){
				if(!hasAttributeValue(classAttribute, classValueObj)){
					return;
				}
			}
			if(classValueObj!=null){
				if(isNumericValue(traingingClassPropertyDescriptor)
//						Classes.contains(wekaClassPropertyDescriptor.getReadMethod().getClass().getName())
						){
					instance.setValue(classAttribute,Double.valueOf(classValueObj.toString()));
				}else{
					instance.setValue(classAttribute,classValueObj.toString());
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

	/**分類用Instanceを生成する*/
	public Instance createClassifyInstance(T obj){
		Instance  instance = super.createInstance(obj, attributeFastVector.size()+1);
		instance.setDataset(instances);
		return instance;
	}

	@Override public Instances getInstances() {
		if(instances!=null)return instances;
		instances = new Instances(clazz.getName(), getAttributeFastVector(), list.size());
		//最後のattributeをclassに指定する。
		instances.setClassIndex(getAttributeFastVector().size()-1);
		for(T obj:list){
			instances.add(createLearningInstance(obj));
		}
		return instances;
	}

}

