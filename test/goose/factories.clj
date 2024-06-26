(ns goose.factories
  (:require [goose.brokers.redis.commands :as redis-cmds]
            [goose.brokers.redis.cron :as cron]
            [goose.brokers.redis.scheduler :as scheduler]
            [goose.defaults :as d]
            [goose.job :as j]
            [goose.retry :as retry]
            [goose.utils :as u]
            [goose.test-utils :as tu]))

(defn job [overrides]
  (merge (j/new `tu/my-fn (list "foobar") tu/queue (d/prefix-queue tu/queue) retry/default-opts)
         overrides))

(defn job-description [overrides]
  (merge (j/description `tu/my-fn (list "foobar") tu/queue (d/prefix-queue tu/queue) retry/default-opts)
         overrides))

(defn cron-opts [overrides]
  (merge {:cron-name     (str (random-uuid))
          :cron-schedule "*/3 * * * *"
          :timezone      "US/Pacific"} overrides))

(defn create-async-job [& [overrides]]
  (let [j (job overrides)]
    (redis-cmds/enqueue-back tu/redis-conn (:ready-queue j) j)
    (:id j)))

(defn create-schedule-job [& [overrides]]
  (let [{:keys [scheduled-at] :as j} (job (merge {:scheduled-at (+ (u/epoch-time-ms) 1000000)} overrides))]
    (scheduler/run-at tu/redis-conn scheduled-at j)
    (:id j)))

(defn create-periodic-job [& [overrides]]
  (let [job-desc (job-description (:job-description overrides))
        cron-opts (cron-opts (:cron-opts overrides))]
    (cron/register tu/redis-conn cron-opts job-desc)
    cron-opts))

(defn create-dead-job [& [overrides]]
  (let [error-state {:state {:error           "Error"
                             :last-retried-at (u/epoch-time-ms),
                             :first-failed-at 1701344365468,
                             :retry-count     27,
                             :retry-at        1701344433359}}
        j (job (merge error-state overrides))]
    (redis-cmds/enqueue-sorted-set tu/redis-conn d/prefixed-dead-queue
                                   (get-in j [:state :last-retried-at]) j)
    (:id j)))

(defn create-jobs [{:keys [enqueued scheduled periodic dead]
                    :or   {enqueued 0 scheduled 0 periodic 0 dead 0}} & [overrides]]
  (let [apply-fn-n-times (fn [n f & args]
                           (dotimes [_ n] (apply f args)))]
    (apply-fn-n-times enqueued create-async-job (:enqueued overrides))
    (apply-fn-n-times scheduled create-schedule-job (:scheduled overrides))
    (apply-fn-n-times periodic create-periodic-job (:periodic overrides))
    (apply-fn-n-times dead create-dead-job (:dead overrides))))
