import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('deploy_five', Path(__file__).with_name('deploy-once.py'))
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)


class DatabaseBoundaryTest(unittest.TestCase):
    def setUp(self):
        network = 'restaurant-pos-staging_restaurant-pos'
        self.backend = {'NetworkSettings': {'Networks': {network: {'IPAddress': '172.19.0.3'}}},
                        'Config': {'Entrypoint': ['sh', '-c', 'java $JAVA_OPTS -jar /app/app.jar'], 'Cmd': None}}
        self.database = {'NetworkSettings': {'Networks': {network: {'IPAddress': '172.19.0.2'}}}, 'Config': {'Env': ['POSTGRES_DB=restaurant_pos_staging']}}
        self.production = {'NetworkSettings': {'Networks': {'cloud_restaurant-pos': {'IPAddress': '172.18.0.2'}}}}
        self.env = {'DB_HOST': 'db', 'DB_PORT': '5432', 'DB_NAME': 'restaurant_pos_staging'}

    def check(self):
        driver.require_staging_database(self.env, self.backend, self.database, self.production)

    def test_exact_staging_database_is_allowed(self):
        self.check()
        self.env['SPRING_DATASOURCE_URL'] = 'jdbc:postgresql://db:5432/restaurant_pos_staging'
        self.check()

    def test_production_name_or_address_is_rejected_before_start(self):
        for url in ['jdbc:postgresql://db:5432/restaurant_pos',
                    'jdbc:postgresql://172.18.0.2:5432/restaurant_pos_staging',
                    'jdbc:postgresql://cloud-db-1:5432/restaurant_pos_staging']:
            self.env['SPRING_DATASOURCE_URL'] = url
            with self.assertRaises(AssertionError): self.check()

    def test_external_config_or_java_override_is_rejected(self):
        for key, value in [('SPRING_APPLICATION_JSON', '{"spring":{"datasource":{}}}'),
                           ('JAVA_OPTS', '-Dspring.datasource.url=other'),
                           ('SPRING_CONFIG_IMPORT', 'file:/other/config')]:
            self.env[key] = value
            with self.assertRaises(AssertionError): self.check()
            del self.env[key]

    def test_production_network_overlap_is_rejected(self):
        self.production['NetworkSettings']['Networks'].update(self.database['NetworkSettings']['Networks'])
        with self.assertRaises(AssertionError): self.check()

    def test_cloud_profile_inputs_cannot_select_production(self):
        self.env['DB_NAME'] = 'restaurant_pos'
        with self.assertRaises(AssertionError): self.check()


if __name__ == '__main__': unittest.main()
