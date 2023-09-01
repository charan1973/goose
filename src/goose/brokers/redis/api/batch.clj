(ns ^:no-doc goose.brokers.redis.api.batch
  (:require
    [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
    [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
    [goose.brokers.redis.batch :as batch]
    [goose.brokers.redis.commands :as redis-cmds]))

(defn status [redis-conn id]
  (batch/get-batch-state redis-conn id))

(defn- delete-enqueued-jobs
  [redis-conn
   {:keys [queue]}
   enqueued-job-set]
  (let [enqueued-job-ids (redis-cmds/set-members redis-conn enqueued-job-set)]
    (doseq [job-id enqueued-job-ids]
      (when-let [job (enqueued-jobs/find-by-id redis-conn queue job-id)]
        (enqueued-jobs/delete redis-conn job)))))

(defn- delete-retrying-jobs
  [redis-conn
   {{:keys [retry-queue ready-retry-queue]} :retry-opts :keys [queue ready-queue]}
   retrying-job-set]
  (let [retried-job-ids (redis-cmds/set-members redis-conn retrying-job-set)]
    (doseq [job-id retried-job-ids]
      (if-let [job-scheduled-for-retry (scheduled-jobs/find-by-id redis-conn job-id)]
        (scheduled-jobs/delete redis-conn job-scheduled-for-retry)
        (let [queue (or retry-queue queue)
              retry-or-ready-queue (or ready-retry-queue ready-queue)]
          (when-let [job-enqueued-for-retry (enqueued-jobs/find-by-id redis-conn queue job-id)]
            (enqueued-jobs/delete redis-conn job-enqueued-for-retry retry-or-ready-queue)))))))

(defn delete [redis-conn id]
  (let [{:keys [batch-hash-key enqueued-job-set retrying-job-set successful-job-set dead-job-set]} (batch/batch-keys id)
        batch (batch/get-batch-state redis-conn id)]

    (delete-enqueued-jobs redis-conn batch enqueued-job-set)
    (delete-retrying-jobs redis-conn batch retrying-job-set)
    (redis-cmds/del-keys redis-conn batch-hash-key enqueued-job-set retrying-job-set successful-job-set dead-job-set)))
