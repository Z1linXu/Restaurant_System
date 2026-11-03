import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('finish_deploy',Path(__file__).with_name('deploy-staging.py'))
d=importlib.util.module_from_spec(spec);spec.loader.exec_module(d)
V39='1|old|1|t\n39|V39__add_dining_table_split_label_mode.sql|1431202837|t'
V40=V39+'\n40|V40__order_finish_batches.sql|123|t'
class StagingOnlyTest(unittest.TestCase):
    def test_only_exact_append_migration_is_allowed(self):
        d.validate_ledger(V39,V40)
        for after in [V39,V40.replace('old','changed'),V40.replace('V40__order_finish_batches','V40__wrong'),V40.replace('|123|t','|123|f')]:
            with self.assertRaises(AssertionError):d.validate_ledger(V39,after)
    def test_only_staging_and_exact_sha_can_reach_existing_lifecycle(self):
        with patch.object(d.release.i,'ledger',return_value=V39),patch.object(d.release,'main') as start:
            d.main('a'*40);start.assert_called_once_with('staging','a'*40)
            with self.assertRaises(AssertionError):d.main('production')
            with self.assertRaises(AssertionError):d.release.read_sql('cloud-db-1','select 1')
    def test_wrong_baseline_does_not_deploy(self):
        with patch.object(d.release.i,'ledger',return_value=V40),patch.object(d.release,'main') as start:
            with self.assertRaises(AssertionError):d.main('a'*40)
            start.assert_not_called()
class AcceptanceGuardTest(unittest.TestCase):
    def test_writes_are_only_owned_synthetic_orders_and_store18_batch(self):
        spec=importlib.util.spec_from_file_location('finish_acceptance',Path(__file__).with_name('acceptance.py'))
        a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)
        api=a.Api(100)
        with patch.object(a.a.h.Api,'call',return_value={'id':101,'store_id':18}):
            api.call('/orders','POST',{'store_id':18})
            api.call('/orders/101/submit','POST',{})
            api.call('/stores/18/orders/finish-all-tables','POST',{'preview_token':'test'})
            for path,body in [('/orders',{'store_id':1}),('/orders/99/cancel',{}),('/stores/1/orders/finish-all-tables',{}),('/admin/printing/status',{}),('/devices/register',{})]:
                with self.assertRaises(AssertionError):api.call(path,'POST',body)
            self.assertEqual(a.a.h.API_BASE,'https://staging-pos.lanzhounoodlesmtl.com/api/v1')
if __name__=='__main__':unittest.main()
