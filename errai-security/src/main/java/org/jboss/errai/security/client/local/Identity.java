package org.jboss.errai.security.client.local;

import com.google.gwt.user.client.Cookies;
import org.jboss.errai.bus.client.api.base.MessageBuilder;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.jboss.errai.databinding.client.api.Bindable;
import org.jboss.errai.ioc.client.container.IOC;
import org.jboss.errai.security.shared.SecurityManager;
import org.jboss.errai.ui.nav.client.local.Navigation;
import static org.jboss.errai.ui.nav.client.local.Navigation.CURRENT_PAGE_COOKIE;

import javax.enterprise.context.SessionScoped;
import java.io.Serializable;

/**
 * @author edewit@redhat.com
 */
@Bindable
@SessionScoped
public class Identity implements Serializable {
  private String username;
  private String password;

  public boolean isLoggedIn() {
    //TODO add user management
    return false;//user != null;
  }

  public void login() {
    MessageBuilder.createCall(new RemoteCallback<Void>() {
      @Override
      public void callback(final Void response) {
      }
    }, SecurityManager.class).login(username, password);
    final String page = Cookies.getCookie(CURRENT_PAGE_COOKIE);
    if (page != null) {
      Cookies.removeCookie(CURRENT_PAGE_COOKIE);
      IOC.getBeanManager().lookupBean(Navigation.class).getInstance().goTo(page);
    }
  }

  public void logout() {
    MessageBuilder.createCall(new RemoteCallback<Void>() {
      @Override
      public void callback(final Void response) {
      }
    }, SecurityManager.class).logout();
  }

  public boolean hasPermission(Object resource, String operation) {
    //TODO add role management
    return false;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}