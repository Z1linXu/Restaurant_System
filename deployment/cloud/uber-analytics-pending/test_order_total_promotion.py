import importlib.util,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('six_promotion',Path(__file__).with_name('promote-order-total.py'))
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
class SixPromotionTests(unittest.TestCase):
    def metrics(self):
        return {'1':{'totals':{'total':{'order_count':107,'known_revenue':4425.51,'revenue':4425.51,'average_order_value':41.36,'unknown_line_count':18,'item_pending_order_count':6},'in_store':{'order_count':61,'revenue':2707.04},'uber_eats':{'order_count':46,'known_revenue':1718.47,'revenue':1718.47,'unknown_order_count':0}},'categories':[{'total':{'percentage':None}}]}}
    def test_approved_new_baseline_and_independent_item_completeness(self):
        a=self.metrics();p.totals(a,True)
        a['1']['totals']['total']['order_count']=106
        with self.assertRaises(AssertionError):p.totals(a,True)
    def test_no_complete_category_percentage_when_items_unknown(self):
        a=self.metrics();a['1']['categories'][0]['total']['percentage']=100
        with self.assertRaises(AssertionError):p.totals(a,True)
    def test_sql_or_diagnostic_failure_cannot_skip_app_restore(self):
        for save_error in (None,OSError('DISK_FULL')):
            with patch.object(p,'read',return_value={'preview_sha256':'a'*64}),patch.object(p,'sql',side_effect=RuntimeError('DB_DOWN')),patch.object(p,'save',side_effect=save_error),patch.object(p,'compose') as compose,patch.object(p.m.d.prior,'wait_health'):
                if save_error:
                    with self.assertRaises(OSError):p.restore_application({})
                else:p.restore_application({})
                compose.assert_called_once()
                self.assertIn('rollback.private.json',str(compose.call_args.args[0]))
    def test_failed_health_cannot_skip_restoring_both_canonical_markers(self):
        with patch.object(p,'read',return_value={'preview_sha256':'a'*64}),patch.object(p,'sql',return_value='0'),patch.object(p,'save') as save,patch.object(p,'compose'),patch.object(p.m.d.prior,'wait_health',side_effect=RuntimeError('DB_DOWN')):
            with self.assertRaisesRegex(RuntimeError,'DB_DOWN'):p.restore_application({})
            paths=[str(c.args[0]) for c in save.call_args_list]
            self.assertIn(str(p.BASE/'current.compose.json'),paths)
            self.assertIn(str(p.BASE/'release.json'),paths)

    def test_manual_app_restore_rejects_another_release(self):
        with patch.object(p,'read',return_value={'before':{},'backend':'new','nginx':'new'}),patch.object(p.m.i,'inspect',return_value={'Image':'unrelated'}),patch.object(p,'restore_application') as restore:
            with self.assertRaisesRegex(AssertionError,'Another release'):p.rollback()
            restore.assert_not_called()
    def test_cli_cannot_default_to_legacy_fourteen_order_case(self):
        with tempfile.TemporaryDirectory() as temp,patch.object(p,'ROOT',Path('/tmp/unused')):
            p.ROOT=Path(temp)
            with patch.object(p,'read',side_effect=[{'preview_sha256':'a'*64},{'backend':'new-image'}]),patch.object(p.m.ops,'env_of',return_value={k:'synthetic' for k in ('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD')}),patch.object(p.m.i,'inspect',return_value={}),patch.object(p,'run',return_value=b'{"inserted":6}') as run:
                self.assertEqual(p.cli('apply')['inserted'],6)
                args=run.call_args.args[0]
                self.assertEqual(args[args.index('--case')+1],'order-total-v3')
                self.assertEqual(args[args.index('--preview-sha')+1],'a'*64)
                self.assertEqual((p.ROOT/'cli.private.env').stat().st_mode&0o777,0o600)
    def test_legacy_comparison_keeps_financial_values(self):
        self.assertEqual(p.stable({'revenue':1,'item_pending_order_count':6,'item_pending_reasons':{'X':6},'unknown_order_count':0}),{'revenue':1,'unknown_order_count':0})
if __name__=='__main__':unittest.main()
