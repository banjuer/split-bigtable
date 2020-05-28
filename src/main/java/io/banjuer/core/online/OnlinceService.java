package io.banjuer.core.online;

import io.banjuer.config.em.SqlType;
import io.banjuer.core.BaseService;
import io.banjuer.core.SelectParser;
import io.banjuer.core.work.CleanWorker;
import io.banjuer.core.work.SelectWorker;
import io.banjuer.helper.BaseJdbcTemplate;
import io.banjuer.helper.JdbcHelper;
import io.banjuer.helper.MySQLJdbcTemplate;
import io.banjuer.web.entity.BaseResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class OnlinceService extends BaseService {

    ExecutorService executor = new ThreadPoolExecutor(8, 32, 30, TimeUnit.MINUTES, new ArrayBlockingQueue<>(1024));

    public BaseResponse execueSql(String sql) throws InterruptedException {
        // SelectParser parse = SelectParser.parse(sql);
        SelectParser parse = new SelectParser(sql);
        SqlType sqlType = parse.getSqlType();
        return switch (sqlType) {
            case update -> doUpdate(parse);
            case insert -> doInsert(parse);
            case select -> doSelect(parse);
            case delete -> doDelete(parse);
        };
    }

    /**
     * TODO
     */
    private BaseResponse doDelete(SelectParser parse) {
        return null;
    }

    private BaseResponse doSelect(SelectParser parse) throws InterruptedException {
        MySQLJdbcTemplate template = JdbcHelper.INSTANCE.getMysqlJdbc();
        if (isCache(parse.getSqlKey())) {
            return BaseResponse.success();
        }
        prepareSelect(parse, template);
        mapSelect(parse, template);
        return reduceSelect(parse, template);
    }

    private void mapSelect(SelectParser parse, BaseJdbcTemplate template) throws InterruptedException {
        String[] shardValues = parse.getReferShardValues();
        CountDownLatch latch = new CountDownLatch(shardValues.length);
        for (String shardValue : shardValues) {
            executor.submit(new SelectWorker(latch, template, parse.getSelect(shardValue)));
        }
        latch.await();
    }

    private BaseResponse reduceSelect(SelectParser parse, BaseJdbcTemplate template) {
        List<Object[]> rows = new ArrayList<>();
        template.executeQuery(parse.getReduceSql(), null, rs -> {
            int count = rs.getMetaData().getColumnCount();
            Object[] row = new Object[count];
            while (rs.next()) {
                for (int i = 0; i < row.length; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                rows.add(row);
            }
        });
        executor.submit(new CleanWorker(parse.getSqlKey(), template, rows));
        return BaseResponse.success(rows);
    }

    /**
     * 提前建表
     */
    private void prepareSelect(SelectParser parse, BaseJdbcTemplate template) {
        String select0 = parse.getSelect0();
        template.executeUpdate(select0, null);
    }
    
    private boolean isCache(String sqlKey) {
        return false;
    }

    private BaseResponse doInsert(SelectParser parse) {
        return null;
    }

    private BaseResponse doUpdate(SelectParser parse) {
        return null;
    }

    @Override
    public BaseResponse<Double> getProgress(String key) {
        return null;
    }

}
