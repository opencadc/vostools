#!python
DROPBOX_API_KEY = """w3bx4yv2t1mr5rv"""
DROPBOX_API_SECRET = """ufrr2je8dqvlu9q"""
DROPBOX_REV_TAG = "revision"
VOSPACE_REV_TAG = "ivo://gpi/dropbox#revision"

import dropbox
from vos import vos
import os
from cStringIO import StringIO
import sys
import time
import logging

logger = logging.getLogger(__name__)
#logger.addHandler(logging.StreamHandler())
logger.setLevel(logging.INFO)

source = sys.argv[1]
dest = sys.argv[2]

db_token_file  = os.path.join(os.getenv('HOME'),'dropbox.token')
if not os.access(db_token_file, os.R_OK):
   flow = dropbox.client.DropboxOAuth2FlowNoRedirect(DROPBOX_API_KEY,
                                                     DROPBOX_API_SECRET)
   authorize_url = flow.start()
   
   print '1. Go to: ' + authorize_url
   print '2. Click "Allow" (you might have to log in first)'
   print '3. Copy the authorization code.'
   code = raw_input("Enter the authorization code here: ").strip()
   
   access_token, user_id = flow.finish(code)
   fout = file(db_token_file,'wn')
   fout.write(access_token)
   fout.close()
else:
   access_token = file(db_token_file,'rb').read()

db_client = dropbox.client.DropboxClient(access_token)
vo_client = vos.Client()


vos_root = source
dropbox_root = dest

for node_name in vo_client.listdir(vos_root):
   node_uri = vos_root + "/" + node_name
   node = vo_client.get_node(node_uri, force=True)
   if node.isdir():
      continue
   try:
      db_meta = db_client.metadata(dropbox_root+"/"+node_name, list=False)
      if str(db_meta[DROPBOX_REV_TAG]) == node.props[VOSPACE_REV_TAG]:
         logger.info("Skipping %s" % ( node_name))
         continue
      else:
	 logger.debug("rev: %s vs %s" % (str(db_meta[DROPBOX_REV_TAG]), node.props[VOSPACE_REV_TAG]))
   except Exception as e:
      logger.debug(str(e))
      pass

   logger.info("Pulling %s from VOSpace" % (node_name))
   fobj = StringIO(vo_client.open(node_uri, view="data").read())
   logger.info("Putting %s  to  Dropbox" % (node_name))
   db_meta = db_client.put_file(dropbox_root+'/'+node_name,fobj, overwrite=True)
   
   logger.debug("Updating node property with rev tag")
   node.props[VOSPACE_REV_TAG] = str(db_meta[DROPBOX_REV_TAG])
   vo_client.add_props(node)

   logger.debug("Next")
   

@app.route('/')
def home():
   if not 'access_token' in session:
      return redirect(url_for('dropbox_auth_start'))
   return 'Authenticated.'

@app.route('/dropbox-auth-start')
def dropbox_auth_start():
   return redirect(get_auth_flow().start())

@app.route('/dropbox-auth-finish')
def dropbox_auth_finish():
   try:
      access_token, user_id, url_state = get_auth_flow().finish(request.args)
   except:
      abort(400)
   else:
      session['access_token'] = access_token
   return redirect(url_for('home'))

def get_auth_flow():
   redirect_url = url_for('dropbox_auth_finish', _external=True)
   return DropboxOAuth2Flow(DROPBOX_APP_KEY, DROPBOX_API_SECRET, redirect_uri,
                            session, 'dropbox-auth-csrf-token')


