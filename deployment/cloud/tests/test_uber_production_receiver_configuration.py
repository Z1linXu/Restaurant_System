"""No-network checks; fixture keys are unrelated to any runtime credential."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import json
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('production_receiver',
    Path(__file__).resolve().parents[1]/'configure-uber-production-receiver.py')
receiver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(receiver)


class ReceiverConfigurationTest(unittest.TestCase):
    def setUp(self):
        self.env = {'APP_ENVIRONMENT':'production','UBER_EATS_ENVIRONMENT':'production',
            'UBER_EATS_PROCESSING_MODE':'KITCHEN_MIRROR','UBER_EATS_WORKER_ENABLED':'false',
            'UBER_EATS_CLIENT_ID':receiver.ops.PROD_CLIENT_ID,'UBER_EATS_SCOPES':receiver.ops.SCOPES,
            'UBER_EATS_STORE_CUTOVERS':'{}','UBER_EATS_ENABLED':'false',
            'UBER_EATS_CLIENT_SECRET':'fixture-retained-oauth','OTHER_SETTING':'retained'}

    def test_only_receiver_and_primary_key_change(self):
        original = copy.deepcopy(self.env)
        actual = receiver.receiver_env(self.env,'fixture-primary-signing-key')
        self.assertEqual(self.env,original)
        self.assertEqual(actual['UBER_EATS_ENABLED'],'true')
        self.assertEqual(actual['UBER_EATS_WORKER_ENABLED'],'false')
        for key,value in self.env.items():
            if key != 'UBER_EATS_ENABLED': self.assertEqual(actual[key],value)

    def test_rejects_active_worker_foreign_environment_and_cutovers(self):
        for key,value in [('UBER_EATS_WORKER_ENABLED','true'),('APP_ENVIRONMENT','staging'),
            ('UBER_EATS_ENVIRONMENT','sandbox'),('UBER_EATS_PROCESSING_MODE','ORDER_MANAGER'),
            ('UBER_EATS_STORE_CUTOVERS','{"fixture":"value"}'),
            ('UBER_EATS_PROCESSING_NOT_BEFORE','2026-10-22T00:00:00Z')]:
            with self.subTest(key=key),self.assertRaises(receiver.ops.PromotionError):
                receiver.receiver_env(dict(self.env,**{key:value}),'fixture-key')

    def test_invalid_input_never_embeds_secret_in_error(self):
        for value in [None,{},'', ' ', '\nfixture-secret', 'fixture-secret\r', 'x'*4097]:
            with self.subTest(kind=type(value).__name__):
                with self.assertRaisesRegex(receiver.ops.PromotionError,'^SIGNING_KEY_INPUT_INVALID$'):
                    receiver.receiver_env(self.env,value)

    def test_acceptance_requires_durable_single_held_row(self):
        counts={'local_orders':0}
        responses=[(401,b''),(400,b''),(200,b''),(200,b'')]
        with patch.object(receiver,'business_counts',return_value=counts), \
             patch.object(receiver.verify,'request',side_effect=responses), \
             patch.object(receiver.verify,'psql',return_value={'count':2,'held':2}):
            with self.assertRaisesRegex(receiver.ops.PromotionError,'DURABLE_RECEIPT_NOT_PROVEN'):
                receiver.accept({'UBER_EATS_WEBHOOK_SIGNING_KEY':'fixture-key'})

    def test_acceptance_rejects_business_side_effect(self):
        responses=[(401,b''),(400,b''),(200,b''),(200,b'')]
        with patch.object(receiver,'business_counts',side_effect=[{'local_orders':0},{'local_orders':1}]), \
             patch.object(receiver.verify,'request',side_effect=responses), \
             patch.object(receiver.verify,'psql',return_value={'count':1,'held':1}):
            with self.assertRaisesRegex(receiver.ops.PromotionError,'UBER_BUSINESS_SIDE_EFFECT'):
                receiver.accept({'UBER_EATS_WEBHOOK_SIGNING_KEY':'fixture-key'})

    def test_smoke_system_exit_rolls_back_runtime_and_private_model(self):
        image,sha='sha256:'+'a'*64,'b'*40
        runtime=dict(self.env)
        changes=[]
        def inspect(name):
            if name == image: return {'Config':{'Labels':{'org.opencontainers.image.revision':sha}}}
            return {'Id':name+'-id','Image':image,'Config':{'Env':[k+'='+v for k,v in
                (runtime if name=='cloud-backend-1' else {}).items()]}}
        def run(args,**kwargs):
            if args[1]=='ps': return b'cloud-backend-1\ncloud-db-1\n'
            if args[1]=='exec': return b'archive-list'
            model=json.loads(Path(args[5]).read_text())
            if 'config' in args: return json.dumps(model).encode()
            changes.append(Path(args[5]).name)
            runtime.clear();runtime.update(model['services']['backend']['environment'])
            return b''
        def dump(*args,**kwargs):
            kwargs['stdout'].write(b'fixture-dump')
            return type('Result',(),{'returncode':0})()
        with tempfile.TemporaryDirectory() as folder:
            base=Path(folder).resolve();(base/'production').mkdir()
            source=base/'production/current.compose.json'
            receiver.ops.write_private(source,{'services':{'backend':{'image':image,'environment':self.env}}})
            with patch.object(receiver.ops,'BASE',base), \
                 patch.object(receiver.ops,'operation_locks',__import__('contextlib').nullcontext), \
                 patch.object(receiver.ops,'inspect',side_effect=inspect), \
                 patch.object(receiver.ops,'run',side_effect=run), \
                 patch.object(receiver.ops,'inventory',return_value={}), \
                 patch.object(receiver.ops,'queue_guard'), \
                 patch.object(receiver.ops,'wait_healthy'), \
                 patch.object(receiver.subprocess,'run',side_effect=dump), \
                 patch.object(receiver,'business_counts',return_value={}), \
                 patch.object(receiver.verify,'request',side_effect=[(401,b''),(400,b''),(200,b''),(200,b'')]), \
                 patch.object(receiver.verify,'psql',return_value={'count':1,'held':1}), \
                 patch.object(receiver.verify.smoke,'numeric_identity',side_effect=SystemExit('fixture-hidden-detail')):
                with self.assertRaisesRegex(receiver.ops.PromotionError,'^RECEIVER_FAILED_SAFE_ROLLBACK_APPLIED$'):
                    receiver.configure(image,sha,'fixture-key')
            self.assertEqual(changes,['target.private.json','rollback.private.json'])
            self.assertEqual(runtime['UBER_EATS_ENABLED'],'false')
            self.assertEqual(runtime['UBER_EATS_WORKER_ENABLED'],'false')
            self.assertEqual(json.loads(source.read_text())['services']['backend']['environment'],runtime)


if __name__ == '__main__':
    unittest.main()
