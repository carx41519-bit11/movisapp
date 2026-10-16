"""Photo-based manual stock movements must retain normal stock safeguards."""
import unittest
from unittest.mock import patch
from test_rice import RiceTests
from service import Error


class StockMovementTests(unittest.TestCase):
    setUp = RiceTests.setUp
    tearDown = RiceTests.tearDown
    req = RiceTests.req
    stock = RiceTests.stock

    def verified_photo(self, kind, quantities, user=None, source='camera'):
        user = user or self.staff
        aid = self.r.open(user, self.req(kind=kind, products=list(quantities)))['action_id']
        self.r.image(user, {'action_id': aid, 'image_id': aid+'-photo', 'source': source,
                           'zoom': 1, 'manual_only': True, 'image': self.photo})
        action = self.r.state(user, aid)['action']
        self.assertEqual(action['images'][0]['model_version'], 'manual-verified-photo')
        self.assertEqual(action['images'][0]['raw'], [])
        self.r.review(user, self.req(action_id=aid, revision=action['revision'], image_id=aid+'-photo',
                                    checked=[{'product_id': p, 'quantity': q} for p,q in quantities.items()], confirmed=True))
        return aid

    def submit_payload(self, aid, user=None):
        return self.req(action_id=aid, revision=self.r.state(user or self.staff, aid)['action']['revision'],
                        reason='Physically verified warehouse movement', unique_sacks=True)

    def test_manual_photo_skips_model_and_stock_changes_only_once_after_submit(self):
        self.s.mode = 'yolo'
        with patch.object(self.s, 'model') as detector:
            detector.predict.side_effect = RuntimeError('Inference unavailable')
            aid = self.verified_photo('stock-in', {1: 3, 3: 4})
            detector.predict.assert_not_called()
        self.assertEqual(self.stock(), [20,20,20])
        payload=self.submit_payload(aid)
        self.r.submit(self.staff,payload)
        self.r.submit(self.staff,payload)
        self.assertEqual(self.stock(),[23,20,24])

    def test_stock_out_subtracts_once_and_records_previous_balance(self):
        aid=self.verified_photo('stock-out',{1:5,3:2})
        payload=self.submit_payload(aid)
        self.r.submit(self.staff,payload);self.r.submit(self.staff,payload)
        self.assertEqual(self.stock(),[15,20,18])
        history=self.r.state(self.staff)['history']
        self.assertEqual(sorted((r['previous_quantity'],r['difference'],r['quantity']) for r in history),[(20,-5,15),(20,-2,18)])

    def test_insufficient_stock_rolls_back_all_products_and_keeps_draft(self):
        aid=self.verified_photo('stock-out',{1:2,3:21})
        with self.assertRaises(Error) as exc:self.r.submit(self.staff,self.submit_payload(aid))
        self.assertEqual(exc.exception.status,409)
        self.assertEqual(self.stock(),[20,20,20])
        self.assertEqual(self.r.state(self.staff)['history'],[])
        self.assertEqual(self.r.state(self.staff,aid)['action']['state'],'open')

    def test_website_evidence_is_admin_only(self):
        aid=self.r.open(self.staff,self.req(kind='stock-in',products=[1]))['action_id']
        with self.assertRaises(Error) as exc:
            self.r.image(self.staff,{'action_id':aid,'image_id':'staff-upload','source':'upload','zoom':1,'manual_only':True,'image':self.photo})
        self.assertEqual(exc.exception.status,403)
        aid=self.verified_photo('stock-in',{1:2},self.admin,'upload')
        self.r.submit(self.admin,self.submit_payload(aid,self.admin))
        self.assertEqual(self.stock(),[22,20,20])

    def test_unverified_photo_and_invalid_counting_mode_cannot_post_stock(self):
        aid=self.r.open(self.staff,self.req(kind='stock-in',products=[1]))['action_id']
        with self.assertRaises(Error):
            self.r.image(self.staff,{'action_id':aid,'image_id':'invalid','source':'camera','zoom':1,'manual_only':'yes','image':self.photo})
        self.r.image(self.staff,{'action_id':aid,'image_id':'unreviewed','source':'camera','zoom':1,'manual_only':True,'image':self.photo})
        with self.assertRaises(Error):self.r.submit(self.staff,self.submit_payload(aid))
        self.assertEqual(self.stock(),[20,20,20])

    def test_product_alias_edits_remain_linked_to_detector(self):
        self.r.product(self.admin,{'product_id':3,'sku':'RICE-BUK-25','description':'Verified Buko Pandan sack','model_class':'bukopandan_rice'})
        self.assertEqual(self.r.state(self.admin)['products'][2]['model_class'],'buko_pandan')


del RiceTests
