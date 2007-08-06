# Generated by the windmill services transformer
from windmill.authoring import WindmillTestClient

def test():
    client = WindmillTestClient(__name__)

    client.wait(milliseconds=5000)
    client.click(jsid=u'windmill.testingApp.cosmo.app.pim.layout.baseLayout.mainApp.centerColumn.navBar.viewToggle.buttonNodes[1].id')
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)
    client.doubleClick(id=u'hourDiv1-1200')
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv1-1300'}, dragged={u'pfx': u'eventDivBottom__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv1-800'}, dragged={u'pfx': u'eventDivTop__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.doubleClick(id=u'hourDiv3-900')
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv3-900'}, dragged={u'pfx': u'eventDivBottom__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv3-800'}, dragged={u'pfx': u'eventDivTop__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.doubleClick(id=u'hourDiv4-1000')
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv4-1100'}, dragged={u'pfx': u'eventDivBottom__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv4-800'}, dragged={u'pfx': u'eventDivTop__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.doubleClick(id=u'hourDiv5-1100')
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv5-1200'}, dragged={u'pfx': u'eventDivBottom__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv4-1000'}, dragged={u'pfx': u'eventDivTop__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.doubleClick(id=u'hourDiv1-1200')
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv1-1400'}, dragged={u'pfx': u'eventDivBottom__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv1-1100'}, dragged={u'pfx': u'eventDivTop__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv3-1200'}, dragged={u'pfx': u'eventDivContent__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.doubleClick(id=u'hourDiv2-1100')
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv2-800'}, dragged={u'pfx': u'eventDivTop__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv4-800'}, dragged={u'pfx': u'eventDivContent__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.doubleClick(id=u'hourDiv4-1000')
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv4-1200'}, dragged={u'pfx': u'eventDivBottom__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv2-1530'}, dragged={u'pfx': u'eventDivContent__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.doubleClick(id=u'hourDiv5-1100')
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv5-1130'}, dragged={u'pfx': u'eventDivContent__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.doubleClick(id=u'hourDiv1-1200')
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)
    client.extensions.cosmoDragDrop(destination={u'id': u'hourDiv1-900'}, dragged={u'pfx': u'eventDivContent__', u'jsid': u'windmill.testingApp.cosmo.view.cal.canvasInstance.getSelectedItemId()'})
    client.wait(milliseconds=5000)
    client.wait(milliseconds=5000)