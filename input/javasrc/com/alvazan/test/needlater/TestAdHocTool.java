package com.alvazan.test.needlater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.alvazan.orm.api.base.DbTypeEnum;
import com.alvazan.orm.api.base.NoSqlEntityManager;
import com.alvazan.orm.api.base.NoSqlEntityManagerFactory;
import com.alvazan.orm.api.spi3.meta.DboColumnCommonMeta;
import com.alvazan.orm.api.spi3.meta.DboColumnMeta;
import com.alvazan.orm.api.spi3.meta.DboColumnToOneMeta;
import com.alvazan.orm.api.spi3.meta.DboDatabaseMeta;
import com.alvazan.orm.api.spi3.meta.DboTableMeta;
import com.alvazan.orm.api.spi3.meta.NoSqlSessionFactory;
import com.alvazan.orm.api.spi3.meta.conv.StandardConverters;
import com.alvazan.orm.api.spi5.NoSqlSession;
import com.alvazan.orm.api.spi9.db.Column;
import com.alvazan.orm.api.spi9.db.Row;
import com.alvazan.orm.impl.bindings.BootstrapImpl;
import com.alvazan.test.FactorySingleton;

public class TestAdHocTool {

	@Test
	public void testOrmLayerMetaSaved() {
		NoSqlEntityManagerFactory factory = FactorySingleton.createFactoryOnce();
		NoSqlEntityManager mgr = factory.createEntityManager();

		DboDatabaseMeta database = mgr.find(DboDatabaseMeta.class, DboDatabaseMeta.META_DB_ROWKEY);
		DboTableMeta table = database.getMeta("Activity");
		DboColumnMeta columnMeta = table.getColumnMeta("account");
		DboColumnToOneMeta toOne = (DboColumnToOneMeta) columnMeta;
		Assert.assertEquals("id", toOne.getFkToColumnFamily().getIdColumnMeta().getColumnName());
		
	}
	
	//@Test
	public void testBasic() {
		DboDatabaseMeta metaDb = new DboDatabaseMeta();
		addMetaClassDbo(metaDb, "MyEntity", "cat", "mouse", "dog");
		addMetaClassDbo(metaDb, "OtherEntity", "id", "dean", "declan", "pet", "house");

		NoSqlSessionFactory factory = BootstrapImpl.createRawInstance(DbTypeEnum.IN_MEMORY, metaDb);
		String sql = "ON /someindex select * FROM MyEntity e WHERE e.cat = \"deano\"";

		NoSqlSession session = factory.createSession();
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("cat", "deano");
		//session.addToIndex("/someindex", "myId", map);
		byte[] myId = StandardConverters.convertToBytes("myId");
		List<Column> columns = new ArrayList<Column>();
		session.put("MyEntity", myId, columns);
		
		session.flush();
		
		List<Row> rows = factory.runQuery(sql);
		Assert.assertEquals(1, rows.size());
	}

	private void addMetaClassDbo(DboDatabaseMeta map, String entityName, String ... fields) {
		DboTableMeta meta = new DboTableMeta();
		meta.setColumnFamily(entityName);
		map.addMetaClassDbo(meta);
		
		for(String field : fields) {
			DboColumnCommonMeta fieldDbo = new DboColumnCommonMeta();
			fieldDbo.setup(meta, field, String.class, true, false);
		}
	}
}