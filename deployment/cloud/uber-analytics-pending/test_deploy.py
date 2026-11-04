import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('pending_deploy',Path(__file__).with_name('deploy.py'))
d=importlib.util.module_from_spec(spec);spec.loader.exec_module(d)

class BoundedReleaseTest(unittest.TestCase):
    def test_database_failure_cannot_skip_application_rollback(self):
        with patch.object(d,'read',return_value={'previewSha256':'a'*64}), patch.object(d,'sql',side_effect=RuntimeError('DB_DOWN')), \
             patch.object(d,'save') as save, patch.object(d,'compose') as compose, patch.object(d.d.prior,'wait_health'):
            d.rollback_failed_release(Path('/private/release'),True,{'services':{}})
            compose.assert_called_once()
            self.assertIn('rollback.private.json',str(compose.call_args.args[0]))
            self.assertTrue(any('compensation-failure.json' in str(c.args[0]) for c in save.call_args_list))

    def test_only_images_and_labels_change(self):
        before={'services':{s:{'image':'old','environment':{'DB_HOST':'db'},'networks':['shared']} for s in d.SERVICES}}
        after=d.copy.deepcopy(before);images={s:'new-'+s for s in d.SERVICES}
        for s in d.SERVICES:after['services'][s]['image']=images[s]
        d.d.validate_delta(before,after,images)
        after['services']['backend']['environment']['DB_HOST']='other'
        with self.assertRaises(AssertionError):d.d.validate_delta(before,after,images)
    def test_incomplete_financial_totals_stay_null(self):
        data={'1':{'totals':{'total':{'order_count':106,'known_revenue':4042.31,'revenue':None,'average_order_value':None},
              'in_store':{'order_count':60,'revenue':2639.25},'uber_eats':{'order_count':46,'known_revenue':1403.06,'unknown_amount_count':6,'unknown_order_count':6}}}}
        d.verify_totals(data,True)
        data['1']['totals']['total']['revenue']=4042.31
        with self.assertRaises(AssertionError):d.verify_totals(data,True)
    def test_regression_projection_retains_legacy_financial_values(self):
        self.assertEqual(d.stable({'known_revenue':1,'unknown_order_count':4,'daily':[{'pending_reasons':{'X':1},'revenue':None}]}),
                         {'known_revenue':1,'daily':[{'revenue':None}]})
if __name__=='__main__':unittest.main()
