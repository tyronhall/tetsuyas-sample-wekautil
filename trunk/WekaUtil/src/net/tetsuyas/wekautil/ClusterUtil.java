package net.tetsuyas.wekautil;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.log4j.Logger;

import weka.clusterers.Clusterer;
import weka.core.Attribute;
import weka.core.Instances;

import net.tetsuyas.wekautil.annotations.ClassifiedClassAttribute;
import net.tetsuyas.wekautil.annotations.ClusterResultAttribute;

public class ClusterUtil<T> extends WekaUtil<T> {
	public ClusterUtil(List<? extends T> list) {
		super(list);
		getClusterer();
	}


	private static Logger logger = Logger.getLogger(ClusterUtil.class);

	Clusterer clusterer;
	PropertyDescriptor clusterResultPropertyDescriptor;
	Attribute clusterResultAttribute;

	@Override
	boolean registSubClassPropertyDescriptor(PropertyDescriptor pd,Annotation[] annotations) {
		if(hasAnnotation(ClusterResultAttribute.class, annotations)
				|| (clusterResultPropertyDescriptor!=null && pd.getName().toLowerCase()
					.equals(ClusterResultAttribute.class.getSimpleName().toLowerCase()))){

			/**クラスターの結果intでなければならない。*/
				if(!pd.getReadMethod().getReturnType().equals(int.class)
					&& !pd.getReadMethod().getReturnType().equals(Integer.class)){
					logger.error("clusterResultAttribute should be int or Integer .");
					throw new IllegalStateException("clusterResultAttribute should be int or Integer .");
				}
				clusterResultPropertyDescriptor = pd;
				return true;
		}

		/**当該クラスがClassifierにも対応している場合、当該アノテーションを持つプロパティは登録しない。*/
		if(hasAnnotation(ClassifiedClassAttribute.class, annotations)){
			return true;
		}

		return false;
	}

	@Override
	void registSubClassAttributeFastVector() {
		if(isNumericValue(clusterResultPropertyDescriptor)){
			clusterResultAttribute =new Attribute(clusterResultPropertyDescriptor.getName());
		}else{
			clusterResultAttribute = createAttributeNominalValues(clusterResultPropertyDescriptor);
		}

		attributeMap.put(clusterResultPropertyDescriptor, clusterResultAttribute);
	}

	public Clusterer getClusterer() {
		return clusterer;
	}

	public void setClusterer(Clusterer clusterer) {
		this.clusterer = clusterer;
	}

	public List<? extends T> buildClusterer() throws Exception{
		if(clusterResultPropertyDescriptor==null)
			throw new IllegalStateException("clusterResultPropertyDescriptor is NULL!!");
			clusterer.buildClusterer(getInstances());
			Method writeMethod = clusterResultPropertyDescriptor.getWriteMethod();
			for(T obj:list){
				writeMethod.invoke(obj, clusterer.clusterInstance(
					createInstance(obj, attributeFastVector.size())));
			}
			return list;
	}

	@Override
	public Instances getInstances() {
		if(instances!=null)return instances;
		instances = new Instances(clazz.getName(), getAttributeFastVector(), list.size());
		for(T obj:list) instances.add(createInstance(obj, attributeFastVector.size()));
		return instances;
	}


}

