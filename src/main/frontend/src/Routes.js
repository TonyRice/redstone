import React from "react";
import { Route, Switch, Redirect } from "react-router-dom";
import Home from "./containers/Home";
import Login from "./containers/Login";

import AuthenticatedRoute from "./routers/AuthenticatedRoute";
import UnauthenticatedRoute from "./routers/UnauthenticatedRoute";


export default function Routes({ appProps }) {
  return (
    <Switch>
      <Route path="/" exact component={Home} appProps={appProps} />
      <UnauthenticatedRoute path="/login" exact component={Login} appProps={appProps} />
      <Route exact path="*" render={() => (
        <Redirect to="/" />
      )} />
    </Switch>
  );
}