package com.zsj.core.test.controller.proxy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class MyTransactionService {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Transactional
	public void transaction(){

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
			/**
			 * 挂起
			 */
			@Override
			public void suspend() {
				TransactionSynchronization.super.suspend();
			}

			/**
			 * 恢复
			 */
			@Override
			public void resume() {
				TransactionSynchronization.super.resume();
			}

			@Override
			public void flush() {
				TransactionSynchronization.super.flush();
			}

			/**
			 * 提交前
			 * @param readOnly whether the transaction is defined as read-only transaction
			 */
			@Override
			public void beforeCommit(boolean readOnly) {
				TransactionSynchronization.super.beforeCommit(readOnly);
			}

			/**
			 * 完成前
			 */
			@Override
			public void beforeCompletion() {
				TransactionSynchronization.super.beforeCompletion();
			}

			/**
			 * 提交后
			 */
			@Override
			public void afterCommit() {
				TransactionSynchronization.super.afterCommit();
			}

			/**
			 * 完成后
			 * @param status completion status according to the {@code STATUS_*} constants
			 */
			@Override
			public void afterCompletion(int status) {
				TransactionSynchronization.super.afterCompletion(status);
			}
		});

		System.out.println(TransactionSynchronizationManager.getCurrentTransactionName());

//		TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

		jdbcTemplate.execute("UPDATE PERTAX SET TAX_ID = 1111 WHERE CLIENT_ID = 1 AND ACTIVE_IND = 'Y'");

	}
}
