import copy
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
import retire_template as r

class RetirementOwnershipTests(unittest.TestCase):
    def setUp(self):
        self.containers = [dict(id=str(i)*64, name='/' + r.PROJECT+'-'+service+'-1', image='sha256:'+'a'*64,
            running=False, restarting=False, project=r.PROJECT, service=service, mounts=[], networks={})
            for i,service in enumerate(r.SERVICES,1)]
        self.other=dict(id='9'*64,name='/production',image='sha256:'+'b'*64,running=True,restarting=False,
            project='cloud',service='backend',mounts=[],networks={})
        self.volumes=[{'Name':r.VOLUME,'Driver':'local','Labels':{'com.docker.compose.project':r.PROJECT,'com.docker.compose.volume':'test_postgres'}}]
        self.networks=[{'Id':'a'*64,'Name':r.NETWORKS[0],'Driver':'bridge','Labels':{'com.docker.compose.project':r.PROJECT},'Containers':{}}]
    def plan(self):return r.plan_resources(self.containers+[self.other],self.volumes,self.networks)
    def test_exact_stopped_project_is_selected_with_full_ids(self):
        p=self.plan();self.assertEqual(4,len(p['containers']));self.assertEqual(1,len(p['volumes']));self.assertEqual(1,len(p['networks']));self.assertEqual([],p['retained'])
    def test_running_or_restarting_old_name_fails_closed(self):
        for key in ['running','restarting']:
            rows=copy.deepcopy(self.containers);rows[0][key]=True
            with self.assertRaises(ValueError):r.plan_resources(rows,[],[])
    def test_project_or_service_collision_is_never_name_authority(self):
        for key in ['project','service']:
            rows=copy.deepcopy(self.containers);rows[0][key]='foreign'
            with self.assertRaises(ValueError):r.plan_resources(rows,[],[])
    def test_truncated_id_cannot_be_a_deletion_target(self):
        self.containers[0]['id']='abc123'
        with self.assertRaises(ValueError):self.plan()
    def test_shared_volume_is_retained_even_when_other_container_stopped(self):
        self.other['running']=False;self.other['mounts']=[{'Type':'volume','Name':r.VOLUME}]
        p=self.plan();self.assertEqual([],p['volumes']);self.assertEqual('volume',p['retained'][0]['kind'])
    def test_foreign_volume_label_is_retained(self):
        self.volumes[0]['Labels']['com.docker.compose.project']='cloud';self.assertEqual([],self.plan()['volumes'])
    def test_shared_or_foreign_network_is_retained(self):
        self.networks[0]['Containers']={self.other['id']:{}}
        self.assertEqual([],self.plan()['networks'])
        self.networks[0]['Containers']={};self.other['networks']={r.NETWORKS[0]:{}}
        self.assertEqual([],self.plan()['networks'])
    def test_db_bind_is_retained_and_images_are_never_targeted(self):
        self.containers[-1]['mounts']=[{'Type':'bind','Source':'/synthetic/test/db'}]
        p=self.plan();self.assertTrue(p['images'].startswith('RETAINED'));self.assertEqual('bind',p['retained'][0]['kind'])
    def test_volume_bind_alias_or_descendant_is_retained(self):
        with tempfile.TemporaryDirectory() as folder:
            volume=Path(folder)/'volume';volume.mkdir();child=volume/'child';child.mkdir()
            alias=Path(folder)/'alias';alias.symlink_to(volume,target_is_directory=True)
            self.volumes[0]['Mountpoint']=str(volume)
            for source in [volume,child,alias,Path(folder)]:
                self.other['mounts']=[{'Type':'bind','Source':str(source)}]
                self.assertEqual([],self.plan()['volumes'])
    def test_proven_disjoint_bind_does_not_block_own_volume(self):
        with tempfile.TemporaryDirectory() as folder:
            volume=Path(folder)/'volume';volume.mkdir();other=Path(folder)/'other';other.mkdir()
            self.volumes[0]['Mountpoint']=str(volume);self.other['mounts']=[{'Type':'bind','Source':str(other)}]
            self.assertEqual(1,len(self.plan()['volumes']))
    def test_special_volume_options_or_driver_are_retained(self):
        for key,value in [('Driver','nfs'),('Options',{'device':'/foreign'})]:
            old=copy.deepcopy(self.volumes);self.volumes[0][key]=value
            self.assertEqual([],self.plan()['volumes']);self.volumes=old
    def test_config_parent_or_alias_mount_is_retained(self):
        with tempfile.TemporaryDirectory(dir="/private/tmp" if Path("/private/tmp").is_dir() else "/tmp") as folder:
            directory=Path(folder)/'private';directory.mkdir();file=directory/'runtime.env';file.write_text('synthetic')
            alias=Path(folder)/'alias';alias.symlink_to(directory,target_is_directory=True)
            with patch.object(r,'ENV_FILE',file):
                self.assertIsNotNone(r.env_identity([]))
                for source in [directory,file,alias]:
                    self.other['mounts']=[{'Type':'bind','Source':str(source)}]
                    self.assertIsNone(r.env_identity([self.other]))
    def test_unknown_path_alias_fails_closed(self):
        self.assertTrue(r.paths_overlap('/missing/unknown','/also/missing'))
    def test_apply_wrong_digest_or_changed_plan_makes_no_mutation(self):
        plan={'synthetic':True}
        with patch.object(r,'run') as command,patch.object(r,'build_plan',return_value={'synthetic':False}):
            with self.assertRaises(ValueError):r.apply_plan(plan,'0'*64)
            with self.assertRaises(ValueError):r.apply_plan(plan,r.digest(plan))
            command.assert_not_called()
    def test_partial_failure_records_exact_completed_objects_without_restore(self):
        plan={'resources':self.plan(),'protected':{},'ingress':{},'private_env':None}
        with patch.object(r,'build_plan',return_value=plan),patch.object(r,'run',side_effect=[b'',RuntimeError('bounded refusal')]) as command:
            result=r.apply_plan(plan,r.digest(plan))
            self.assertEqual('FAIL_PARTIAL',result['status']);self.assertEqual(1,len(result['deleted']));self.assertFalse(result['automatic_old_test_restore'])
            self.assertEqual(['docker','rm','1'*64],command.call_args_list[0].args[0])

if __name__=='__main__':unittest.main()
