package com.alvazan.orm.impl.meta.scan;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import com.alvazan.orm.api.base.anno.Column;
import com.alvazan.orm.api.base.anno.Id;
import com.alvazan.orm.api.base.anno.Indexed;
import com.alvazan.orm.api.base.anno.ManyToOne;
import com.alvazan.orm.api.base.anno.NoConversion;
import com.alvazan.orm.api.base.anno.NoSqlEntity;
import com.alvazan.orm.api.base.anno.OneToMany;
import com.alvazan.orm.api.base.anno.OneToOne;
import com.alvazan.orm.api.base.spi.KeyGenerator;
import com.alvazan.orm.api.spi3.db.conv.Converter;
import com.alvazan.orm.api.spi3.db.conv.StandardConverters;
import com.alvazan.orm.impl.meta.data.IdInfo;
import com.alvazan.orm.impl.meta.data.MetaClass;
import com.alvazan.orm.impl.meta.data.MetaCommonField;
import com.alvazan.orm.impl.meta.data.MetaField;
import com.alvazan.orm.impl.meta.data.MetaIdField;
import com.alvazan.orm.impl.meta.data.MetaInfo;
import com.alvazan.orm.impl.meta.data.MetaListField;
import com.alvazan.orm.impl.meta.data.MetaProxyField;
import com.alvazan.orm.impl.meta.data.ReflectionUtil;

@SuppressWarnings("rawtypes")
public class ScannerForField {
	@Inject
	private MetaInfo metaInfo;
	@Inject
	private Provider<MetaIdField> idMetaProvider;
	@Inject
	private Provider<MetaCommonField> metaProvider;
	@Inject
	private Provider<MetaListField> metaListProvider;
	@Inject
	private Provider<MetaProxyField> metaProxyProvider;
	
	private Map<Class, Converter> customConverters = new HashMap<Class, Converter>();

	
	public ScannerForField() {

	}
	
	@SuppressWarnings("unchecked")
	public <T> MetaIdField<T> processId(Field field, MetaClass<T> metaClass) {
		if(!String.class.isAssignableFrom(field.getType()))
			throw new IllegalArgumentException("The id is not of type String and has to be.  field="+field+" in class="+field.getDeclaringClass());
		
		Method idMethod = getIdMethod(field);
		
		Id idAnno = field.getAnnotation(Id.class);
		MetaIdField<T> metaField = idMetaProvider.get();
		KeyGenerator gen = null;
		if(idAnno.usegenerator()) {
			Class<? extends KeyGenerator> generation = idAnno.generation();
			gen = ReflectionUtil.create(generation);
		}
		
		Class<?> type = field.getType();
		Converter converter = null;
		if(!NoConversion.class.isAssignableFrom(idAnno.customConverter()))
			converter = ReflectionUtil.create(idAnno.customConverter());
		
		String columnName = field.getName();
		if(!"".equals(idAnno.columnName()))
			columnName = idAnno.columnName();
		String indexPrefix = null;
		if(field.isAnnotationPresent(Indexed.class))
			indexPrefix = "/"+metaClass.getColumnFamily()+"/"+columnName;
		
		try {
			converter = lookupConverter(type, converter);
			IdInfo info = new IdInfo();
			info.setIdMethod(idMethod);
			info.setConverter(converter);
			info.setGen(gen);
			info.setUseGenerator(idAnno.usegenerator());
			info.setMetaClass(metaClass);
			metaField.setup(info, field, columnName, indexPrefix);
			return metaField;
		} catch(IllegalArgumentException e)	{
			throw new IllegalArgumentException("No converter found for field='"+field.getName()+"' in class="
					+field.getDeclaringClass()+".  You need to either add on of the @*ToOne annotations, @Embedded, " +
							"or add your own converter calling EntityMgrFactory.setup(Map<Class, Converter>) which " +
							"will then work for all fields of that type OR add @Column(customConverter=YourConverter.class)" +
							" or @Id(customConverter=YourConverter.class) " +
							" or finally if we missed a standard converter, we need to add it in file "+getClass()+
							" in the constructor and it is trivial code(and we can copy the existing pattern)");
		}		 
	}

	private Method getIdMethod(Field field) {
		String name = field.getName();
		String newName = name.substring(0,1).toUpperCase() + name.substring(1);
		String methodName = "get"+newName; 
		
		Class<?> declaringClass = field.getDeclaringClass();
		try {
			Method method = declaringClass.getDeclaredMethod(methodName);
			if(!method.getReturnType().equals(field.getType()))
				throw new IllegalArgumentException("The method="+declaringClass.getName()+"."+methodName+" must" +
						" return the type="+field.getType().getName()+" but instead returns="+method.getReturnType().getName());
			
			return method;
		} catch (SecurityException e) {
			throw new RuntimeException("security issue on looking up method="+declaringClass.getName()+"."+methodName, e);
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException("You are missing a method "+declaringClass.getName()+"."+methodName
					+"  This method exists as when you call it on a proxy, we make sure we do NOT hit the database" +
					" and instead just return the id that is inside the proxy.  Without this, we can't tell the" +
					" difference between a call to getName(where we have to hit the db and fill the proxy in) and" +
					" a call to just getting the id", e);
		}
	}

	public MetaField processColumn(Field field, String cf) {
		Column col = field.getAnnotation(Column.class);
		MetaCommonField metaField = metaProvider.get();
		String colName = field.getName();
		if(col != null) {
			if(!"".equals(col.columnName()))
				colName = col.columnName();
		}

		String indexPrefix = null;
		if(field.getAnnotation(Indexed.class) != null)
			indexPrefix = "/"+cf+"/"+colName;
		
		Class<?> type = field.getType();
		Converter converter = null;
		if(col != null && !NoConversion.class.isAssignableFrom(col.customConverter()))
			converter = ReflectionUtil.create(col.customConverter());

		try {
			converter = lookupConverter(type, converter);
			metaField.setup(field, colName, converter, indexPrefix);
			return metaField;			
		} catch(IllegalArgumentException e)	{
			throw new IllegalArgumentException("No converter found for field='"+field.getName()+"' in class="
					+field.getDeclaringClass()+".  You need to either add one of the @*ToOne annotations, @Embedded, @Transient " +
							"or add your own converter calling EntityMgrFactory.setup(Map<Class, Converter>) which " +
							"will then work for all fields of that type OR add @Column(customConverter=YourConverter.class)" +
							" or finally if we missed a standard converter, we need to add it in file InspectorField.java" +
							" in the constructor and it is trivial code(and we can copy the existing pattern)");
		}
	}
	
	private Converter lookupConverter(Class<?> type, Converter custom) {
		if(custom != null) {
			return custom;
		} else if(customConverters.get(type) != null) {
			return customConverters.get(type);
		} else if(StandardConverters.get(type) != null){
			return StandardConverters.get(type);
		}
		throw new IllegalArgumentException("bug, caller should catch this and log info about field or id converter, etc. etc");
	}

	public MetaField processEmbeddable(Field field) {
		throw new UnsupportedOperationException("not implemented yet");
	}

	public void setCustomConverters(Map<Class, Converter> converters) {
		if(converters == null)
			return; //nothing to do
		
		this.customConverters = converters;
	}

	public MetaField processManyToOne(Field field, String colFamily) {
		ManyToOne annotation = field.getAnnotation(ManyToOne.class);
		String colName = annotation.columnName();
		return processToOne(field, colFamily, colName);
	}

	public MetaField processOneToOne(Field field, String colFamily) {
		OneToOne annotation = field.getAnnotation(OneToOne.class);
		String colName = annotation.columnName();
		
		return processToOne(field, colFamily, colName);
	}
	
	public MetaField processManyToMany(Field field) {
		OneToMany annotation = field.getAnnotation(OneToMany.class);
		String colName = annotation.columnName();
		Class entityType = annotation.entityType();
		String keyFieldForMap = annotation.keyFieldForMap();
		
		return processToManyRelationship(field, colName, entityType,
				keyFieldForMap);		
	}
	
	public MetaField processOneToMany(Field field) {
		OneToMany annotation = field.getAnnotation(OneToMany.class);
		String colName = annotation.columnName();
		Class entityType = annotation.entityType();
		String keyFieldForMap = annotation.keyFieldForMap();
		
		return processToManyRelationship(field, colName, entityType,
				keyFieldForMap);
	}

	private MetaField processToManyRelationship(Field field, String colNameOrig,
			Class entityType, String keyFieldForMap) {
		String colName = field.getName();
		if(!"".equals(colNameOrig))
			colName = colNameOrig;
		
		Field fieldForKey = null;

		if(entityType == null)
			throw new RuntimeException("Field="+field+" is missing entityType attribute of OneToMany annotation which is required");
		else if(field.getType().equals(Map.class)) {
			if("".equals(keyFieldForMap))
				throw new RuntimeException("Field="+field+" is a Map so @OneToMany annotation REQUIRES a keyFieldForMap attribute which is the field name in the child entity to use as the key");
			String fieldName = keyFieldForMap;
			
			try {
				fieldForKey = entityType.getDeclaredField(fieldName);
				fieldForKey.setAccessible(true);
			} catch (NoSuchFieldException e) {
				throw new RuntimeException("The annotation OneToMany on field="+field+" references a field="+fieldName+" that does not exist on entity="+entityType.getName());
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			}
		}
		
		return processToMany(field, colName, entityType, fieldForKey);
	}
	
	@SuppressWarnings("unchecked")
	private MetaField processToMany(Field field, String colName, Class entityType, Field fieldForKey) {
		//at this point we only need to verify that 
		//the class referred has the @NoSqlEntity tag so it is picked up by scanner at a later time
		if(!entityType.isAnnotationPresent(NoSqlEntity.class))
			throw new RuntimeException("type="+field.getType()+" needs the NoSqlEntity annotation" +
					" since field has OneToMany annotation.  field="+field.getDeclaringClass().getName()+"."+field.getName());
		
		//field's type must be Map or List right now today
		if(!field.getType().equals(Map.class) && !field.getType().equals(List.class)
				&& !field.getType().equals(Set.class) && !field.getType().equals(Collection.class))
			throw new RuntimeException("field="+field+" must be Set, Collection, List or Map since it is annotated with OneToMany");

		MetaListField metaField = metaListProvider.get();
		MetaClass<?> classMeta = metaInfo.findOrCreate(entityType);
		metaField.setup(field, colName,  classMeta, fieldForKey);
		
		return metaField;
	}

	@SuppressWarnings("unchecked")
	public MetaField processToOne(Field field, String colFamily, String colNameOrig) {
		String colName = field.getName();
		if(!"".equals(colNameOrig))
			colName = colNameOrig;
		
		String indexPrefix = null;
		if(field.getAnnotation(Indexed.class) != null)
			indexPrefix ="/"+colFamily+"/"+colName; 
		
		//at this point we only need to verify that 
		//the class referred has the @NoSqlEntity tag so it is picked up by scanner at a later time
		if(!field.getType().isAnnotationPresent(NoSqlEntity.class))
			throw new RuntimeException("type="+field.getType()+" needs the NoSqlEntity annotation" +
					" since field has *ToOne annotation.  field="+field.getDeclaringClass().getName()+"."+field.getName());
		
		MetaProxyField metaField = metaProxyProvider.get();
		MetaClass<?> classMeta = metaInfo.findOrCreate(field.getType());
		
		metaField.setup(field, colName, classMeta, indexPrefix);
		return metaField;
	}

}
